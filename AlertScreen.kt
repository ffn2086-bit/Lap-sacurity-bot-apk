package com.sentinel.laptopguard.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.laptopguard.AlertInfo
import com.sentinel.laptopguard.fmtTime
import kotlinx.coroutines.delay

@Composable
fun AlertScreen(alert: AlertInfo, onOpenPanel: () -> Unit, onDismiss: () -> Unit) {
    val transition = rememberInfiniteTransitionCompat()
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    var entered by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        delay(30)
        entered = 1f
    }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = entered,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "alertScale"
    )
    val fade by androidx.compose.animation.core.animateFloatAsState(
        targetValue = entered,
        animationSpec = tween(350, easing = LinearOutSlowInEasing),
        label = "alertFade"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF2A0003),
                        Color(0xFFB00010).copy(alpha = pulse),
                        Color(0xFF1A0002)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 40.dp)
                .scale(0.9f + 0.1f * scale)
                .alpha(fade),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = tr("alert_subtitle"),
                color = Color(0xFFFFD0D2),
                fontSize = 12.sp,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = alert.title,
                color = Color.White,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(26.dp))

            HudCard(danger = true) {
                InfoRow(tr("alert_time"), fmtTime(alert.time))
                InfoRow(tr("alert_laptop"), alert.laptop)
                InfoRow(tr("alert_local_ip"), alert.localIp)
                InfoRow(tr("alert_public_ip"), alert.publicIp)
                InfoRow(tr("alert_trigger"), alert.reason, valueColor = Color(0xFFFF6A70))
            }

            Spacer(Modifier.height(28.dp))
            MetalButton(
                text = tr("open_panel"),
                onClick = onOpenPanel,
                modifier = Modifier.fillMaxWidth(),
                danger = true
            )
            Spacer(Modifier.height(14.dp))
            MetalButton(
                text = tr("dismiss"),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun rememberInfiniteTransitionCompat() =
    androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
