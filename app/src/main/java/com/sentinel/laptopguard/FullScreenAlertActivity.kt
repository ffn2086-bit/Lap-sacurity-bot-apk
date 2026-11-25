package com.sentinel.laptopguard

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import com.sentinel.laptopguard.ui.AlertScreen
import com.sentinel.laptopguard.ui.AntiTheftTheme

/** Big red warning popup that wakes the screen and shows over the lock screen. */
class FullScreenAlertActivity : ComponentActivity() {

    private var alert by mutableStateOf(AlertInfo())
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        alert = AlertInfo.from(intent)
        startAlarm()

        setContent {
            AntiTheftTheme {
                AlertScreen(
                    alert = alert,
                    onOpenPanel = {
                        stopAlarm()
                        NotificationManagerCompat.from(this).cancel(Notifier.ID_ALERT)
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                        finish()
                    },
                    onDismiss = {
                        stopAlarm()
                        NotificationManagerCompat.from(this).cancel(Notifier.ID_ALERT)
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        alert = AlertInfo.from(intent)
        startAlarm()
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    private fun startAlarm() {
        stopAlarm()
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )
        } catch (e: Exception) {
            // Not allowed while Do Not Disturb is strict; the sound still plays at current volume.
        }
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val r = RingtoneManager.getRingtone(this, uri)
            r.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            r.isLooping = true
            r.play()
            ringtone = r
        } catch (e: Exception) {
        }
        try {
            @Suppress("DEPRECATION")
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 400), 0))
            vibrator = v
        } catch (e: Exception) {
        }
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopAlarm() }, 60_000L)
    }

    private fun stopAlarm() {
        handler.removeCallbacksAndMessages(null)
        try {
            ringtone?.stop()
        } catch (e: Exception) {
        }
        ringtone = null
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
        }
        vibrator = null
    }
}
