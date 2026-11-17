package com.v2ray.ang.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.handler.DeviceLock
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.compose.colorBrandAccent
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

private val DisconnectRed = Color(0xFFFF6B7A)
private val NavyOnAccent = Color(0xFF06222A)

/**
 * Home screen: world map with the connection route, status pill, and a bottom panel with the
 * session timer, live traffic wave, totals, the selected server and the connect button.
 */
@Composable
fun HomeScreen(
    isRunning: Boolean,
    selectedGuid: String?,
    isTesting: Boolean,
    statusText: String,
    traffic: TrafficSession,
    contentPadding: PaddingValues,
    onToggle: () -> Unit,
    onOpenServers: () -> Unit,
    onTestServer: () -> Unit,
    onOpenMenu: () -> Unit,
    onScanQr: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme

    val map by produceState<WorldMapData?>(initialValue = null) {
        value = withContext(Dispatchers.Default) { runCatching { loadWorldMap(context) }.getOrNull() }
    }

    val profile = remember(selectedGuid) { selectedGuid?.let { MmkvManager.decodeServerConfig(it) } }
    val delayMillis = remember(selectedGuid, isTesting, statusText) {
        selectedGuid?.let { MmkvManager.decodeServerAffiliationInfo(it)?.testDelayMillis } ?: 0L
    }
    val remarks = profile?.remarks.orEmpty()
    val flagEmoji = remember(remarks) { findFlagEmoji(remarks) }
    val destIso = remember(remarks, profile?.server, map) { guessCountryIso(remarks, profile?.server, map) }
    val displayFlag = flagEmoji ?: destIso?.let { isoToFlagEmoji(it) }
    val originIso = remember { detectOriginIso(context) }
    val originPoint = originIso?.let { map?.countryPoints?.get(it) }
    val destPoint = destIso?.let { map?.countryPoints?.get(it) }
    val originName = remember(originIso) { originIso?.let { countryDisplayName(it) } }
    val destName = remember(destIso) { destIso?.let { countryDisplayName(it) } }
    val usage by rememberSubscriptionUsage(profile?.subscriptionId?.takeIf { it.isNotBlank() }, isRunning)

    var panelHeightPx by remember { mutableIntStateOf(0) }
    var rootHeightPx by remember { mutableIntStateOf(0) }
    val topPad = contentPadding.calculateTopPadding()
    val bottomPad = contentPadding.calculateBottomPadding()
    val windowTop = with(density) { (topPad + 140.dp).toPx() }
    val panelEstimate = if (panelHeightPx > 0) panelHeightPx.toFloat() else with(density) { 400.dp.toPx() }
    val windowBottom = max(
        rootHeightPx - with(density) { (bottomPad + 20.dp).toPx() } - panelEstimate,
        windowTop + with(density) { 120.dp.toPx() }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { rootHeightPx = it.height }
    ) {
        WorldMapView(
            map = map,
            origin = originPoint,
            destination = destPoint,
            originLabel = originName,
            destinationLabel = destName,
            connected = isRunning,
            windowTop = windowTop,
            windowBottom = windowBottom,
            accent = colorBrandAccent,
            originColor = colorConfigType,
            outlineColor = colorBrandAccent.copy(alpha = 0.30f),
            landColor = scheme.surfaceContainerLow
        )

        // Header: logo, wordmark, quick actions
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = topPad + 12.dp, start = 20.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.requiredSize(58.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = scheme.onSurface)) { append("Void") }
                    withStyle(SpanStyle(color = colorBrandAccent)) { append("Tunnel") }
                },
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            HeaderButton(R.drawable.ic_qu_scan_24dp, "Scan QR", onScanQr)
            Spacer(Modifier.width(8.dp))
            HeaderButton(R.drawable.ic_menu_24dp, "Menu", onOpenMenu)
        }

        HomeStatusPill(
            isRunning = isRunning,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topPad + 12.dp + 40.dp + 16.dp)
        )

        // Bottom panel
        val panelShape = RoundedCornerShape(28.dp)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPad)
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .onSizeChanged { panelHeightPx = it.height }
                .clip(panelShape)
                .background(scheme.surfaceContainer.copy(alpha = 0.95f))
                .border(1.dp, scheme.outlineVariant, panelShape)
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(scheme.onSurfaceVariant.copy(alpha = 0.35f))
            )
            Spacer(Modifier.height(10.dp))
            TimerText(
                traffic = traffic,
                isRunning = isRunning,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(12.dp))
            TrafficWave(traffic = traffic, isRunning = isRunning)
            Spacer(Modifier.height(12.dp))
            StatsRow(traffic = traffic, host = profile?.server)
            Spacer(Modifier.height(12.dp))
            usage?.let {
                SubscriptionUsageBar(it)
                Spacer(Modifier.height(12.dp))
            }
            ServerRow(
                profile = profile,
                flagEmoji = displayFlag,
                removableFlag = flagEmoji,
                destIso = destIso,
                delayMillis = delayMillis,
                onOpenServers = onOpenServers,
                onTestServer = onTestServer
            )
            Spacer(Modifier.height(12.dp))
            ConnectButton(isRunning = isRunning, onClick = onToggle)
        }
    }
}

@Composable
private fun HeaderButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun HomeStatusPill(isRunning: Boolean, modifier: Modifier = Modifier) {
    val color = if (isRunning) colorBrandAccent else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = if (isRunning) 0.55f else 0.3f), shape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Icon(painterResource(R.drawable.ic_shield_24dp), null, Modifier.size(15.dp), tint = color)
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (isRunning) "Connected & secure" else "Not connected",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun TrafficWave(traffic: TrafficSession, isRunning: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase"
    )
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(scheme.surfaceContainerLowest)
            .border(1.dp, scheme.outlineVariant, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.ic_vt_person), null, Modifier.size(22.dp), tint = colorConfigType)
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 8.dp)
        ) {
            val w = size.width
            val h = size.height
            val rx = traffic.rxHistory
            val tx = traffic.txHistory
            val peak = max(max(rx.maxOrNull() ?: 0f, tx.maxOrNull() ?: 0f), 20_000f)
            val samples = 80
            fun buildWave(history: List<Float>, shift: Float): Path {
                val path = Path()
                for (i in 0 until samples) {
                    val idx = history.size - samples + i
                    val v = if (idx >= 0) history[idx] else 0f
                    val norm = (v / peak).coerceIn(0f, 1f)
                    val amp = h * (0.05f + 0.36f * norm)
                    val x = w * i / (samples - 1)
                    val y = h / 2f + sin(i * 0.45f + phase + shift) * amp
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                return path
            }
            val alpha = if (isRunning) 1f else 0.45f
            drawPath(
                buildWave(tx, 1.6f),
                colorConfigType.copy(alpha = 0.9f * alpha),
                style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                buildWave(rx, 0f),
                colorBrandAccent.copy(alpha = alpha),
                style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        Icon(painterResource(R.drawable.ic_vt_server), null, Modifier.size(22.dp), tint = colorBrandAccent)
    }
}

@Composable
private fun StatsRow(traffic: TrafficSession, host: String?) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val down = formatBytes(traffic.rxBytes)
    val up = formatBytes(traffic.txBytes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainerHigh)
            .border(1.dp, scheme.outlineVariant, shape)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatCell(Modifier.weight(1f), R.drawable.ic_vt_arrow_down, "DOWNLOAD", down.first, down.second, colorBrandAccent)
        Box(Modifier.width(1.dp).height(38.dp).background(scheme.outlineVariant))
        StatCell(Modifier.weight(1f), R.drawable.ic_vt_arrow_up, "UPLOAD", up.first, up.second, colorConfigType)
        Box(Modifier.width(1.dp).height(38.dp).background(scheme.outlineVariant))
        StatCell(Modifier.weight(1f), R.drawable.ic_vt_globe, "IP", maskHost(host), "", colorBrandAccent, mono = true)
    }
}

@Composable
private fun StatCell(
    modifier: Modifier,
    @DrawableRes icon: Int,
    label: String,
    value: String,
    unit: String,
    tint: Color,
    mono: Boolean = false
) {
    Column(modifier = modifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, Modifier.size(12.dp), tint = tint)
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tint, letterSpacing = 0.5.sp)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = if (mono) 12.sp else 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = unit,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ServerRow(
    profile: ProfileItem?,
    flagEmoji: String?,
    removableFlag: String?,
    destIso: String?,
    delayMillis: Long,
    onOpenServers: () -> Unit,
    onTestServer: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    val title = when {
        profile == null -> "Select a server"
        else -> {
            val cleaned = if (removableFlag != null) profile.remarks.replace(removableFlag, "").trim() else profile.remarks.trim()
            cleaned.ifBlank { profile.server.orEmpty() }
        }
    }
    val countryName = remember(destIso) { if (destIso == null) "" else countryDisplayName(destIso) }
    val subtitle = when {
        profile == null -> "Tap to choose"
        else -> listOf(
            countryName,
            protocolLabel(profile),
            if (DeviceLock.isLockedProfile(profile)) "\uD83D\uDD12 Locked" else ""
        ).filter { it.isNotBlank() }.joinToString(" \u2022 ")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainerHigh)
            .border(1.dp, scheme.outlineVariant, shape)
            .clickable(onClick = onOpenServers)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val badgeShape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(badgeShape)
                .background(scheme.surfaceContainerHighest)
                .border(1.dp, scheme.outlineVariant, badgeShape),
            contentAlignment = Alignment.Center
        ) {
            if (flagEmoji != null) {
                Text(flagEmoji, fontSize = 24.sp)
            } else {
                Icon(painterResource(R.drawable.ic_shield_24dp), null, Modifier.size(22.dp), tint = scheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (profile != null) {
            PingPill(delayMillis = delayMillis, onClick = onTestServer)
            Spacer(Modifier.width(6.dp))
        }
        Icon(painterResource(R.drawable.ic_vt_chevron_right), null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
    }
}

@Composable
private fun PingPill(delayMillis: Long, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val color = when {
        delayMillis < 0L -> colorPingRed
        delayMillis == 0L -> scheme.onSurfaceVariant
        delayMillis < 150L -> colorPing
        delayMillis < 400L -> Color(0xFFF5B83D)
        else -> colorConfigType
    }
    val text = when {
        delayMillis < 0L -> "timeout"
        delayMillis == 0L -> "test"
        else -> "$delayMillis ms"
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(scheme.surfaceContainerHighest)
            .border(1.dp, scheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun ConnectButton(isRunning: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val base = Modifier
        .fillMaxWidth()
        .height(54.dp)
        .clip(shape)
    val styled = if (isRunning) {
        base
            .background(Brush.horizontalGradient(listOf(Color(0xFF3A0F17), Color(0xFF260A10))))
            .border(1.dp, DisconnectRed.copy(alpha = 0.6f), shape)
    } else {
        base.background(colorBrandAccent)
    }
    Box(
        modifier = styled.clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isRunning) "Disconnect" else "Connect",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (isRunning) DisconnectRed else NavyOnAccent
        )
    }
}

@Composable
private fun TimerText(traffic: TrafficSession, isRunning: Boolean, modifier: Modifier = Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Text(
        text = if (isRunning) formatDuration(traffic.elapsedSec) else "00:00:00",
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        fontSize = 26.sp,
        letterSpacing = 3.sp,
        fontWeight = FontWeight.Medium,
        color = if (isRunning) onSurface.copy(alpha = 0.85f) else onSurface.copy(alpha = 0.35f)
    )
}

internal fun countryDisplayName(iso: String): String =
    Locale("", iso).getDisplayCountry(Locale.getDefault())

private fun protocolLabel(profile: ProfileItem): String {
    val parts = mutableListOf(profile.configType.name)
    profile.network?.takeIf { it.isNotBlank() && !it.equals("tcp", ignoreCase = true) }?.let { parts.add(it) }
    profile.security?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    return parts.joinToString(" / ")
}

private fun maskHost(host: String?): String {
    if (host.isNullOrBlank()) return "\u2014"
    val v4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\$").matchEntire(host)
    return if (v4 != null) {
        "${v4.groupValues[1]}.${v4.groupValues[2]}.\u2022\u2022\u2022.${v4.groupValues[4]}"
    } else {
        host
    }
}
