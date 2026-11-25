package com.sentinel.laptopguard.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.sentinel.laptopguard.L
import com.sentinel.laptopguard.Store

/** Reads the live language from [Store.lang] so every screen recomposes the moment
 *  the user flips the toggle in Settings - no activity restart needed. */
@Composable
fun tr(key: String): String {
    val ctx = LocalContext.current
    val lang by Store.lang.collectAsState()
    return remember(lang, key) { L.t(ctx, key) }
}

object HudColors {
    val Background = Color(0xFF0D0F12)
    val Panel = Color(0xFF15181D)
    val PanelHi = Color(0xFF1C2026)
    val SilverLight = Color(0xFFE5E7EB)
    val Silver = Color(0xFFC0C0C0)
    val SilverDark = Color(0xFF8A8D93)
    val Steel = Color(0xFF5A5D63)
    val Crimson = Color(0xFFFF1E27)
    val CrimsonDark = Color(0xFF8E0A10)
    val Green = Color(0xFF2BFF88)
}

private val HudScheme = darkColorScheme(
    primary = HudColors.Crimson,
    onPrimary = Color.White,
    secondary = HudColors.Silver,
    onSecondary = HudColors.Background,
    background = HudColors.Background,
    onBackground = HudColors.SilverLight,
    surface = HudColors.Panel,
    onSurface = HudColors.SilverLight,
    error = HudColors.Crimson
)

@Composable
fun AntiTheftTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HudScheme, content = content)
}
