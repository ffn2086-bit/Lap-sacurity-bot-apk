package com.sentinel.laptopguard.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.laptopguard.AppLang
import com.sentinel.laptopguard.BackgroundMonitorService
import com.sentinel.laptopguard.Bridge
import com.sentinel.laptopguard.CommandSender
import com.sentinel.laptopguard.L
import com.sentinel.laptopguard.Prefs
import com.sentinel.laptopguard.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun launchSafely(ctx: Context, intent: Intent) {
    try {
        ctx.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(ctx, "Not available on this device", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang by Store.lang.collectAsState()

    var dbUrl by remember { mutableStateOf(Prefs.dbUrl(ctx)) }
    var secret by remember { mutableStateOf(Prefs.secret(ctx)) }
    var monitoring by remember { mutableStateOf(Prefs.monitoring(ctx)) }
    var testResult by remember { mutableStateOf("") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = HudColors.Crimson,
        unfocusedBorderColor = HudColors.Steel,
        cursorColor = HudColors.Crimson,
        focusedLabelColor = HudColors.Crimson,
        unfocusedLabelColor = HudColors.SilverDark,
        focusedTextColor = HudColors.SilverLight,
        unfocusedTextColor = HudColors.SilverLight
    )

    fun save() {
        Prefs.setDbUrl(ctx, dbUrl)
        Prefs.setSecret(ctx, secret)
        dbUrl = Prefs.dbUrl(ctx)
        if (monitoring && dbUrl.isNotEmpty()) {
            try {
                BackgroundMonitorService.start(ctx)
            } catch (e: Exception) {
            }
        }
        Toast.makeText(ctx, L.t(ctx, "saved"), Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            tr("settings_title"),
            color = HudColors.SilverLight,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp
        )

        HudCard {
            SectionLabel(tr("language_title"))
            Spacer(Modifier.height(10.dp))
            Row {
                MetalButton(
                    "English",
                    onClick = {
                        Prefs.setLang(ctx, AppLang.EN)
                        Store.lang.value = AppLang.EN
                    },
                    modifier = Modifier.weight(1f),
                    danger = lang == AppLang.EN
                )
                Spacer(Modifier.width(10.dp))
                MetalButton(
                    "മലയാളം",
                    onClick = {
                        Prefs.setLang(ctx, AppLang.ML)
                        Store.lang.value = AppLang.ML
                    },
                    modifier = Modifier.weight(1f),
                    danger = lang == AppLang.ML
                )
            }
        }

        HudCard {
            SectionLabel(tr("bridge_title"))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = dbUrl,
                onValueChange = { dbUrl = it },
                label = { Text(tr("db_url")) },
                placeholder = { Text("https://your-project-default-rtdb.firebaseio.com") },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text(tr("db_secret")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(tr("bridge_hint"), color = HudColors.SilverDark, fontSize = 12.sp)
        }

        MetalButton(tr("save"), onClick = { save() }, modifier = Modifier.fillMaxWidth())

        MetalButton(
            tr("test_conn"),
            onClick = {
                save()
                testResult = L.t(ctx, "testing")
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        Bridge.get(ctx, "/", "shallow=true") != null
                    }
                    testResult = if (ok) L.t(ctx, "test_ok") else L.t(ctx, "test_fail")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        MetalButton(
            tr("test_alert"),
            onClick = {
                save()
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { CommandSender.sendTestAlert(ctx) }
                    testResult = if (ok) L.t(ctx, "test_alert_ok") else L.t(ctx, "test_alert_fail")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            danger = true
        )

        if (testResult.isNotEmpty()) {
            Text(testResult, color = HudColors.SilverLight, fontSize = 13.sp)
        }

        HudCard {
            SectionLabel(tr("monitoring_title"))
            Spacer(Modifier.height(10.dp))
            MetalButton(
                if (monitoring) tr("monitoring_on") else tr("monitoring_off"),
                onClick = {
                    monitoring = !monitoring
                    Prefs.setMonitoring(ctx, monitoring)
                    if (monitoring) {
                        if (Prefs.dbUrl(ctx).isNotEmpty()) {
                            try {
                                BackgroundMonitorService.start(ctx)
                            } catch (e: Exception) {
                            }
                        }
                    } else {
                        BackgroundMonitorService.stop(ctx)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                danger = monitoring
            )
        }

        HudCard {
            SectionLabel(tr("perms_title"))
            Spacer(Modifier.height(4.dp))
            Text(tr("perms_hint"), color = HudColors.SilverDark, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MetalButton(
                    tr("perm_overlay"),
                    onClick = {
                        launchSafely(
                            ctx,
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + ctx.packageName)
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (Build.VERSION.SDK_INT >= 34) {
                    MetalButton(
                        tr("perm_fullscreen"),
                        onClick = {
                            launchSafely(
                                ctx,
                                Intent(
                                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                    Uri.parse("package:" + ctx.packageName)
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                MetalButton(
                    tr("perm_dnd"),
                    onClick = {
                        launchSafely(ctx, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                MetalButton(
                    tr("perm_battery"),
                    onClick = {
                        launchSafely(
                            ctx,
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + ctx.packageName)
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                MetalButton(
                    tr("perm_notif"),
                    onClick = {
                        launchSafely(
                            ctx,
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        MetalButton(tr("back"), onClick = onBack, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }
}
