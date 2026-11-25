package com.sentinel.laptopguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sentinel.laptopguard.ui.AntiTheftTheme
import com.sentinel.laptopguard.ui.SettingsScreen

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AntiTheftTheme {
                SettingsScreen(onBack = { finish() })
            }
        }
    }
}
