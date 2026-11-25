package com.sentinel.laptopguard

import android.content.Intent
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val ALERT_TITLE = "⚠️ SECURITY BREACH: LAPTOP ACCESSED"

fun fmtTime(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

data class LaptopStatus(
    val name: String = "—",
    val battery: Int = -1,
    val plugged: Boolean = true,
    val ssid: String = "—",
    val localIp: String = "—",
    val lastChangeMs: Long = 0L
)

data class LocationInfo(
    val ssid: String = "—",
    val publicIp: String = "—",
    val city: String = "—",
    val region: String = "—",
    val country: String = "—",
    val isp: String = "—",
    val lat: Double? = null,
    val lon: Double? = null,
    val time: Long = 0L
)

data class CommandResponse(
    val cmd: String,
    val ok: Boolean,
    val msg: String,
    val time: Long
)

data class AlertInfo(
    val title: String = ALERT_TITLE,
    val time: Long = System.currentTimeMillis(),
    val laptop: String = "Laptop",
    val localIp: String = "-",
    val publicIp: String = "-",
    val reason: String = "Unknown"
) {
    fun putInto(i: Intent): Intent {
        i.putExtra("a_title", title)
        i.putExtra("a_time", time)
        i.putExtra("a_laptop", laptop)
        i.putExtra("a_local_ip", localIp)
        i.putExtra("a_public_ip", publicIp)
        i.putExtra("a_reason", reason)
        return i
    }

    companion object {
        fun from(i: Intent): AlertInfo = AlertInfo(
            title = ALERT_TITLE,
            time = i.getLongExtra("a_time", System.currentTimeMillis()),
            laptop = i.getStringExtra("a_laptop") ?: "Laptop",
            localIp = i.getStringExtra("a_local_ip") ?: "-",
            publicIp = i.getStringExtra("a_public_ip") ?: "-",
            reason = i.getStringExtra("a_reason") ?: "Unknown"
        )

        fun fromJson(o: JSONObject): AlertInfo = AlertInfo(
            title = ALERT_TITLE,
            time = o.optLong("time", System.currentTimeMillis()),
            laptop = o.optString("laptop", "Laptop"),
            localIp = o.optString("ip_local", "-"),
            publicIp = o.optString("ip_public", "-"),
            reason = o.optString("reason", "Unknown")
        )
    }
}

/** Shared, observable state written by the monitor service and read by the UI. */
object Store {
    val status = MutableStateFlow(LaptopStatus())
    val armed = MutableStateFlow(true)
    val connected = MutableStateFlow(false)
    val photo = MutableStateFlow<Bitmap?>(null)
    val photoTime = MutableStateFlow(0L)
    val location = MutableStateFlow<LocationInfo?>(null)
    val response = MutableStateFlow<CommandResponse?>(null)
    val lang = MutableStateFlow(AppLang.EN)
}
