package com.sentinel.laptopguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
            Prefs.monitoring(context) &&
            Prefs.dbUrl(context).isNotEmpty()
        ) {
            try {
                BackgroundMonitorService.start(context)
            } catch (e: Exception) {
                // Some OEMs block this; the app will restart the service when opened.
            }
        }
    }
}
