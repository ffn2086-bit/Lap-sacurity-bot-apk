package com.sentinel.laptopguard.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.laptopguard.CommandSender
import com.sentinel.laptopguard.Prefs
import com.sentinel.laptopguard.Store
import com.sentinel.laptopguard.fmtTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Fades + slides a card up into place; each section enters a little later than the one above. */
@Composable
private fun Reveal(delayMs: Long, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        shown = true
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(420)) + slideInVertically(tween(420)) { it / 4 } + expandVertically(tween(420))
    ) {
        content()
    }
}

@Composable
fun Dashboard(onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val status by Store.status.collectAsState()
    val armed by Store.armed.collectAsState()
    val connected by Store.connected.collectAsState()
    val photo by Store.photo.collectAsState()
    val photoTime by Store.photoTime.collectAsState()
    val location by Store.location.collectAsState()
    val response by Store.response.collectAsState()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    var showPowerDialog by remember { mutableStateOf(false) }

    val configured = Prefs.dbUrl(ctx).isNotEmpty()
    val online = connected && status.lastChangeMs > 0L && (now - status.lastChangeMs) < 20_000L

    // IMPORTANT: tr(key) is a @Composable function. It must only ever be called directly
    // inside a @Composable body (e.g. as a Text() argument) - never from inside a plain
    // onClick = { ... } lambda, a coroutine, or a non-composable local fun. So every string
    // that a click handler needs is resolved here, up front, into a plain String val, and
    // the handlers below only ever reference those already-resolved vals.
    val failMsg = tr("test_fail")
    val noMapsMsg = tr("no_maps")
    val lockLabel = tr("lock_now")
    val sirenLabel = tr("siren")
    val stopLabel = tr("stop")
    val photoLabel = tr("photo")
    val locationLabel = tr("location")
    val shutdownLabel = tr("shutdown")
    val sleepLabel = tr("sleep")

    fun sendCmd(cmd: String, label: String) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { CommandSender.send(ctx, cmd) }
            Toast.makeText(
                ctx,
                if (ok) "$label ✓" else failMsg,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val statusText = when {
        !configured -> tr("status_not_configured")
        !connected -> tr("status_cloud_unreachable")
        online -> tr("status_online")
        else -> tr("status_offline")
    }
    val statusColor = if (online) HudColors.Green else HudColors.Crimson

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    tr("app_title"),
                    color = HudColors.SilverLight,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
                Text(
                    tr("app_subtitle"),
                    color = HudColors.Crimson,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
            MetalButton("⚙", onClick = onOpenSettings, modifier = Modifier.width(60.dp))
        }

        Reveal(0) {
            HudCard(danger = !online) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        statusText,
                        color = statusColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
                InfoRow(tr("laptop"), status.name)
                InfoRow(
                    tr("battery"),
                    if (status.battery >= 0) {
                        "${status.battery}%  " + (if (status.plugged) "⚡ " + tr("charging") else tr("on_battery"))
                    } else "—"
                )
                InfoRow(tr("wifi"), status.ssid)
                InfoRow(tr("local_ip"), status.localIp)
                InfoRow(
                    tr("last_beat"),
                    if (status.lastChangeMs == 0L) tr("no_heartbeat")
                    else "${(now - status.lastChangeMs) / 1000}" + tr("ago")
                )
                if (!configured) {
                    Spacer(Modifier.height(10.dp))
                    MetalButton(tr("open_settings"), onClick = onOpenSettings, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Reveal(60) {
            HudCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (armed) tr("armed") else tr("disarmed"),
                            color = if (armed) HudColors.Crimson else HudColors.SilverDark,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            letterSpacing = 2.sp
                        )
                        Text(
                            tr("armed_desc"),
                            color = HudColors.SilverDark,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = armed,
                        onCheckedChange = { value ->
                            Store.armed.value = value
                            scope.launch(Dispatchers.IO) { CommandSender.setArmed(ctx, value) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = HudColors.SilverLight,
                            checkedTrackColor = HudColors.Crimson,
                            uncheckedThumbColor = HudColors.Silver,
                            uncheckedTrackColor = HudColors.Panel
                        )
                    )
                }
            }
        }

        Reveal(120) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(tr("remote_control"))
                MetalButton(
                    lockLabel,
                    onClick = { sendCmd("lock", lockLabel) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetalButton(
                        sirenLabel,
                        onClick = { sendCmd("siren", sirenLabel) },
                        modifier = Modifier.weight(2f),
                        danger = true
                    )
                    MetalButton(
                        stopLabel,
                        onClick = { sendCmd("siren_stop", stopLabel) },
                        modifier = Modifier.weight(1f)
                    )
                }
                MetalButton(
                    photoLabel,
                    onClick = { sendCmd("photo", photoLabel) },
                    modifier = Modifier.fillMaxWidth()
                )
                MetalButton(
                    locationLabel,
                    onClick = { sendCmd("location", locationLabel) },
                    modifier = Modifier.fillMaxWidth()
                )
                MetalButton(
                    tr("power"),
                    onClick = { showPowerDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    danger = true
                )
            }
        }

        val bmp = photo
        if (bmp != null) {
            Reveal(0) {
                Column {
                    SectionLabel(tr("latest_photo"))
                    Spacer(Modifier.height(8.dp))
                    HudCard {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = tr("latest_photo"),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.FillWidth
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            tr("captured") + fmtTime(photoTime),
                            color = HudColors.SilverDark,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        val loc = location
        if (loc != null) {
            Reveal(0) {
                Column {
                    SectionLabel(tr("loc_network"))
                    Spacer(Modifier.height(8.dp))
                    HudCard {
                        InfoRow(tr("wifi"), loc.ssid)
                        InfoRow("PUBLIC IP", loc.publicIp)
                        InfoRow(tr("city"), "${loc.city}, ${loc.region}")
                        InfoRow(tr("country"), loc.country)
                        InfoRow(tr("isp"), loc.isp)
                        if (loc.lat != null && loc.lon != null) {
                            InfoRow(tr("coords"), "${loc.lat}, ${loc.lon}")
                            Spacer(Modifier.height(8.dp))
                            MetalButton(
                                tr("open_maps"),
                                onClick = {
                                    val uri = Uri.parse("geo:${loc.lat},${loc.lon}?q=${loc.lat},${loc.lon}(Laptop)")
                                    try {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, noMapsMsg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            tr("approx") + fmtTime(loc.time),
                            color = HudColors.SilverDark,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        val resp = response
        if (resp != null) {
            Reveal(0) {
                Column {
                    SectionLabel(tr("last_cmd"))
                    Spacer(Modifier.height(8.dp))
                    HudCard(danger = !resp.ok) {
                        InfoRow(tr("command"), resp.cmd)
                        InfoRow(
                            tr("result"),
                            (if (resp.ok) "OK - " else "FAILED - ") + resp.msg,
                            valueColor = if (resp.ok) HudColors.Green else HudColors.Crimson
                        )
                        InfoRow(tr("at"), fmtTime(resp.time))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showPowerDialog) {
        AlertDialog(
            onDismissRequest = { showPowerDialog = false },
            containerColor = HudColors.Panel,
            title = { Text(tr("power_title"), color = HudColors.SilverLight, fontWeight = FontWeight.Bold) },
            text = { Text(tr("power_body"), color = HudColors.Silver) },
            confirmButton = {
                TextButton(onClick = {
                    showPowerDialog = false
                    sendCmd("shutdown", shutdownLabel)
                }) { Text(shutdownLabel, color = HudColors.Crimson, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showPowerDialog = false
                        sendCmd("sleep", sleepLabel)
                    }) { Text(sleepLabel, color = HudColors.SilverLight, fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { showPowerDialog = false }) {
                        Text(tr("cancel"), color = HudColors.SilverDark)
                    }
                }
            }
        )
    }
}
