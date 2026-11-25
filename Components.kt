package com.sentinel.laptopguard.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Brushed-silver tactical button (or crimson when [danger]) with glow, press-scale spring and haptic click. */
@Composable
fun MetalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)
    val fill = if (danger) {
        Brush.verticalGradient(listOf(Color(0xFFFF5A60), HudColors.Crimson, HudColors.CrimsonDark))
    } else {
        Brush.verticalGradient(listOf(HudColors.SilverLight, HudColors.Silver, HudColors.SilverDark))
    }
    val glow = if (danger) HudColors.Crimson else HudColors.SilverLight
    val bevel = Brush.verticalGradient(listOf(Color.White, HudColors.Steel))

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "buttonPress"
    )

    Box(
        modifier = modifier
            .scale(pressScale)
            .alpha(if (enabled) 1f else 0.45f)
            .shadow(
                elevation = if (enabled) 12.dp else 0.dp,
                shape = shape,
                ambientColor = glow,
                spotColor = glow
            )
            .clip(shape)
            .background(fill)
            .border(1.5.dp, bevel, shape)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (danger) Color.White else HudColors.Background,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.5.sp,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

/** Dark panel with a metallic bevel border (crimson when [danger]). */
@Composable
fun HudCard(
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val border = if (danger) {
        Brush.linearGradient(listOf(HudColors.Crimson, HudColors.CrimsonDark))
    } else {
        Brush.verticalGradient(listOf(HudColors.Silver.copy(alpha = 0.8f), HudColors.Steel))
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(HudColors.PanelHi, HudColors.Panel)))
            .border(1.dp, border, shape)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        color = HudColors.SilverDark,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp
    )
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = HudColors.SilverLight) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = HudColors.SilverDark,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.width(96.dp)
        )
        Text(text = value, color = valueColor, fontSize = 14.sp)
    }
}
