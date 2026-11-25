package com.sentinel.laptopguard

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Base64
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Foreground service that polls the Realtime Database every couple of seconds:
 * new alerts -> red full-screen popup, plus status / photo / location / command responses.
 */
class BackgroundMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var lastTs = -1L
    private var lastPhotoTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifier.ensureChannels(this)
        val notification = Notifier.foregroundNotification(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                Notifier.ID_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Notifier.ID_SERVICE, notification)
        }
        running = true

        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sentinel:monitor").apply {
                setReferenceCounted(false)
                acquire()
            }
        }

        if (job == null) {
            job = scope.launch {
                while (isActive) {
                    try {
                        cycle()
                    } catch (e: Exception) {
                        // keep looping no matter what
                    }
                    delay(2000)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        job?.cancel()
        scope.cancel()
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
        }
        wakeLock = null
        super.onDestroy()
    }

    private fun cycle() {
        val ctx = applicationContext
        if (Prefs.dbUrl(ctx).isEmpty()) {
            Store.connected.value = false
            return
        }

        val alerts = Bridge.getObject(ctx, "/alerts", "orderBy=%22%24key%22&limitToLast=5")
        if (alerts == null) {
            Store.connected.value = false
            return
        }
        Store.connected.value = true
        handleAlerts(ctx, alerts)

        updateStatus(ctx)
        updateArmed(ctx)
        updatePhoto(ctx)
        updateLocation(ctx)
        updateResponse(ctx)
    }

    private fun handleAlerts(ctx: Context, alerts: JSONObject) {
        val keys = ArrayList<String>()
        val it = alerts.keys()
        while (it.hasNext()) keys.add(it.next())
        keys.sort()

        if (!Prefs.seeded(ctx)) {
            // First successful poll after (re)configuration: don't replay history.
            Prefs.setLastAlertKey(ctx, if (keys.isEmpty()) "" else keys.last())
            Prefs.setSeeded(ctx, true)
            return
        }
        if (keys.isEmpty()) return

        val last = Prefs.lastAlertKey(ctx)
        val newest = keys.last()
        if (newest <= last) return

        Prefs.setLastAlertKey(ctx, newest)
        val obj = alerts.optJSONObject(newest) ?: return
        val info = AlertInfo.fromJson(obj)

        Notifier.showAlert(ctx, info)
        try {
            val i = info.putInto(Intent(ctx, FullScreenAlertActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ctx.startActivity(i)
        } catch (e: Exception) {
            // Falls back to the full-screen-intent notification.
        }
    }

    private fun updateStatus(ctx: Context) {
        val st = Bridge.getObject(ctx, "/status") ?: return
        if (st.length() == 0) return
        val ts = st.optLong("ts", 0L)
        val first = lastTs == -1L
        val changed = !first && ts != lastTs
        lastTs = ts
        val prev = Store.status.value
        Store.status.value = LaptopStatus(
            name = st.optString("name", "Laptop"),
            battery = st.optInt("battery", -1),
            plugged = st.optBoolean("plugged", true),
            ssid = st.optString("ssid", "—"),
            localIp = st.optString("localIp", "—"),
            lastChangeMs = if (changed) System.currentTimeMillis() else prev.lastChangeMs
        )
    }

    private fun updateArmed(ctx: Context) {
        val t = Bridge.get(ctx, "/config/armed") ?: return
        Store.armed.value = t.trim() != "false"
    }

    private fun updatePhoto(ctx: Context) {
        val pt = Bridge.get(ctx, "/photos/latest/time")?.trim()?.toLongOrNull() ?: return
        if (pt == lastPhotoTime) return
        val raw = Bridge.get(ctx, "/photos/latest/b64") ?: return
        if (raw.trim() == "null") return
        val b64: String = try {
            (JSONTokener(raw).nextValue() as? String) ?: return
        } catch (e: Exception) {
            return
        }
        val bytes = try {
            Base64.decode(b64, Base64.DEFAULT)
        } catch (e: Exception) {
            return
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        lastPhotoTime = pt
        Store.photo.value = bmp
        Store.photoTime.value = pt
    }

    private fun updateLocation(ctx: Context) {
        val o = Bridge.getObject(ctx, "/location/latest") ?: return
        if (o.length() == 0) return
        Store.location.value = LocationInfo(
            ssid = o.optString("ssid", "—"),
            publicIp = o.optString("publicIp", "—"),
            city = o.optString("city", "—"),
            region = o.optString("region", "—"),
            country = o.optString("country", "—"),
            isp = o.optString("isp", "—"),
            lat = if (o.has("lat") && !o.isNull("lat")) o.optDouble("lat") else null,
            lon = if (o.has("lon") && !o.isNull("lon")) o.optDouble("lon") else null,
            time = o.optLong("time", 0L)
        )
    }

    private fun updateResponse(ctx: Context) {
        val o = Bridge.getObject(ctx, "/responses/latest") ?: return
        if (o.length() == 0) return
        Store.response.value = CommandResponse(
            cmd = o.optString("cmd", ""),
            ok = o.optBoolean("ok", false),
            msg = o.optString("msg", ""),
            time = o.optLong("time", 0L)
        )
    }

    companion object {
        @Volatile
        var running = false

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, BackgroundMonitorService::class.java)
            )
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, BackgroundMonitorService::class.java))
        }
    }
}
