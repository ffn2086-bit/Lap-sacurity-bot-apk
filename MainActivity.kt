package com.sentinel.laptopguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.sentinel.laptopguard.ui.AntiTheftTheme
import com.sentinel.laptopguard.ui.Dashboard

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.lang.value = Prefs.lang(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            AntiTheftTheme {
                Dashboard(onOpenSettings = {
                    startActivity(Intent(this, SettingsActivity::class.java))
                })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.monitoring(this) && Prefs.dbUrl(this).isNotEmpty()) {
            try {
                BackgroundMonitorService.start(this)
            } catch (e: Exception) {
            }
        }
    }
}
