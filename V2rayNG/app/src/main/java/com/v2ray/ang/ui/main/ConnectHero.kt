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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
 * logo motif; the ring set gently pulses outward while connected.
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
        Box(
            modifier = Modifier.size(220.dp),
            contentAlignment = Alignment.Center
        ) {
            // Pulsing outer ring, only animates while connected
            if (isRunning) {
                Canvas(modifier = Modifier.size(220.dp)) {
                    val maxRadius = size.minDimension / 2f
                    val radius = maxRadius * (0.62f + pulse * 0.38f)
                    val alpha = (1f - pulse) * 0.35f
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
                        color = ringColor.copy(alpha = if (isRunning) 0.28f - i * 0.07f else 0.5f - i * 0.14f),
                        radius = (size.minDimension / 2f) * fraction,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            // Main circular button
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .shadow(
                        elevation = if (isRunning) 14.dp else 4.dp,
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
                    painter = if (isRunning) painterResource(R.drawable.ic_stop_24dp)
                    else painterResource(R.drawable.ic_play_24dp),
                    contentDescription = if (isRunning) "Disconnect" else "Connect",
                    tint = if (isRunning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(44.dp)
                )
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
