package com.v2ray.ang.ui.main

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.colorBrandAccent
import com.v2ray.ang.ui.compose.colorHeroRingIdleDark
import com.v2ray.ang.ui.compose.colorHeroRingIdleLight

/**
 * Large circular connect/disconnect control shown at the top of the home screen,
 * in the style of V2Box / v2rayTun. Concentric rings echo the app's hexagon
 * logo motif; the ring set gently pulses outward while connected, with a soft
 * neon halo behind the button.
 */
@Composable
fun ConnectHero(
    isRunning: Boolean,
    statusText: String,
    subtitleText: String? = null,
    onToggle: () -> Unit,
) {
    val isDarkTheme = LocalDarkTheme.current
    val idleRingColor = if (isDarkTheme) colorHeroRingIdleDark else colorHeroRingIdleLight

    val infiniteTransition = rememberInfiniteTransition(label = "connectPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StatusPill(isRunning = isRunning)
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier.size(220.dp),
            contentAlignment = Alignment.Center
        ) {
            // Soft neon halo behind everything, only glows while connected
            if (isRunning) {
                Canvas(modifier = Modifier.size(220.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    // layered low-alpha washes approximate a soft glow/bloom
                    val haloSteps = listOf(0.98f to 0.05f, 0.80f to 0.09f, 0.62f to 0.14f)
                    for ((fraction, alpha) in haloSteps) {
                        drawCircle(
                            color = colorBrandAccent.copy(alpha = alpha),
                            radius = (size.minDimension / 2f) * fraction,
                            center = center
                        )
                    }
                }
            }

            // Pulsing outer ring, only animates while connected
            if (isRunning) {
                Canvas(modifier = Modifier.size(220.dp)) {
                    val maxRadius = size.minDimension / 2f
                    val radius = maxRadius * (0.62f + pulse * 0.38f)
                    val alpha = (1f - pulse) * 0.45f
                    drawCircle(
                        color = colorBrandAccent.copy(alpha = alpha),
                        radius = radius,
                        center = Offset(size.width / 2f, size.height / 2f),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            // Static concentric hexagon-echo rings
            Canvas(modifier = Modifier.size(190.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val ringColor = if (isRunning) colorBrandAccent else idleRingColor
                val steps = 3
                for (i in 0 until steps) {
                    val fraction = 1f - (i * 0.16f)
                    drawCircle(
                        color = ringColor.copy(alpha = if (isRunning) 0.34f - i * 0.08f else 0.5f - i * 0.14f),
                        radius = (size.minDimension / 2f) * fraction,
                        center = center,
                        style = Stroke(width = if (isRunning) 2.dp.toPx() else 1.5.dp.toPx())
                    )
                }
            }

            // Main circular button
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .shadow(
                        elevation = if (isRunning) 22.dp else 4.dp,
                        shape = CircleShape,
                        ambientColor = colorBrandAccent,
                        spotColor = colorBrandAccent
                    )
                    .clip(CircleShape)
                    .background(
                        if (isRunning) colorBrandAccent
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shield_24dp),
                    contentDescription = if (isRunning) "Disconnect" else "Connect",
                    tint = if (isRunning) Color(0xFF06222A) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                if (isRunning) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 22.dp, end = 26.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF06222A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_fab_check),
                            contentDescription = null,
                            tint = colorBrandAccent,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        Text(
            text = statusText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isRunning) colorBrandAccent else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )

        if (!subtitleText.isNullOrBlank()) {
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun StatusPill(isRunning: Boolean) {
    val pillColor = if (isRunning) colorBrandAccent else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(pillColor.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(pillColor)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = if (isRunning) "Connected & secure" else "Not connected",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = pillColor
        )
    }
}
