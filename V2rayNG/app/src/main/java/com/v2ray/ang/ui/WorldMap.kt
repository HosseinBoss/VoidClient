package com.v2ray.ang.ui.main

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.tan

/** World outlines plus lookup tables, all in Mercator "map units" (world width = 1000). */
@Immutable
internal class WorldMapData(
    val path: Path,
    val countryPoints: Map<String, Offset>,
    val nameMatchers: List<Pair<Regex, String>>
)

internal object WorldMapProjection {
    const val WORLD_UNITS = 1000f

    fun x(lon: Float): Float = (lon + 180f) / 360f * WORLD_UNITS

    fun y(lat: Float): Float {
        val clamped = lat.coerceIn(-82f, 82f)
        val rad = Math.toRadians(clamped.toDouble())
        val merc = ln(tan(Math.PI / 4.0 + rad / 2.0))
        return (((1.0 - merc / Math.PI) / 2.0) * WORLD_UNITS).toFloat()
    }
}

internal fun loadWorldMap(context: Context): WorldMapData {
    val path = Path()
    context.resources.openRawResource(R.raw.world_map).bufferedReader().useLines { lines ->
        lines.forEach { line ->
            if (line.isBlank()) return@forEach
            var first = true
            line.trim().split(' ').forEach { token ->
                val comma = token.indexOf(',')
                if (comma <= 0) return@forEach
                val lon = token.substring(0, comma).toFloatOrNull() ?: return@forEach
                val lat = token.substring(comma + 1).toFloatOrNull() ?: return@forEach
                val px = WorldMapProjection.x(lon / 10f)
                val py = WorldMapProjection.y(lat / 10f)
                if (first) {
                    path.moveTo(px, py)
                    first = false
                } else {
                    path.lineTo(px, py)
                }
            }
            if (!first) path.close()
        }
    }
    val points = HashMap<String, Offset>()
    context.resources.openRawResource(R.raw.country_points).bufferedReader().useLines { lines ->
        lines.forEach { line ->
            val parts = line.trim().split(' ')
            if (parts.size >= 3) {
                val lon = parts[1].toFloatOrNull()
                val lat = parts[2].toFloatOrNull()
                if (lon != null && lat != null) {
                    points[parts[0].uppercase()] = Offset(WorldMapProjection.x(lon), WorldMapProjection.y(lat))
                }
            }
        }
    }
    val entries = ArrayList<Pair<String, String>>()
    context.resources.openRawResource(R.raw.country_names).bufferedReader().useLines { lines ->
        lines.forEach { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@forEach
            val iso = line.substring(0, tab).trim().uppercase()
            line.substring(tab + 1).split('|').forEach { name ->
                val n = name.trim().lowercase()
                if (n.length >= 4) entries.add(Pair(n, iso))
            }
        }
    }
    // Longest names first so "south korea" wins over "korea".
    val matchers = entries
        .sortedByDescending { it.first.length }
        .map { (name, iso) -> Pair(Regex("(?<![\\p{L}])" + Regex.escape(name) + "(?![\\p{L}])"), iso) }
    return WorldMapData(path, points, matchers)
}

/** Best-effort guess of where the user is: SIM country, then network country, then locale. */
internal fun detectOriginIso(context: Context): String? {
    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
    val candidates: List<String?> = listOf(
        tm?.simCountryIso,
        tm?.networkCountryIso,
        java.util.Locale.getDefault().country
    )
    return candidates.firstOrNull { !it.isNullOrBlank() && it.length == 2 }?.uppercase()
}

/** Finds the first flag emoji (two regional-indicator symbols) anywhere in [text]. */
internal fun findFlagEmoji(text: String): String? {
    if (text.length < 2) return null
    val codePoints = text.codePoints().toArray()
    for (i in 0 until codePoints.size - 1) {
        if (codePoints[i] in 0x1F1E6..0x1F1FF && codePoints[i + 1] in 0x1F1E6..0x1F1FF) {
            return String(codePoints, i, 2)
        }
    }
    return null
}

internal fun flagEmojiToIso(flag: String): String? {
    val codePoints = flag.codePoints().toArray()
    if (codePoints.size < 2) return null
    val a = ('A'.code + (codePoints[0] - 0x1F1E6)).toChar()
    val b = ('A'.code + (codePoints[1] - 0x1F1E6)).toChar()
    return "$a$b"
}

internal fun isoToFlagEmoji(iso: String): String? {
    if (iso.length != 2) return null
    val up = iso.uppercase()
    if (up[0] !in 'A'..'Z' || up[1] !in 'A'..'Z') return null
    val first = 0x1F1E6 + (up[0] - 'A')
    val second = 0x1F1E6 + (up[1] - 'A')
    return String(Character.toChars(first)) + String(Character.toChars(second))
}

private val CODE_ALIASES = mapOf(
    "GER" to "DE", "DEU" to "DE", "NED" to "NL", "NLD" to "NL", "UK" to "GB", "GBR" to "GB",
    "USA" to "US", "UAE" to "AE", "ARE" to "AE", "FRA" to "FR", "TUR" to "TR", "FIN" to "FI",
    "SWE" to "SE", "RUS" to "RU", "KOR" to "KR", "JPN" to "JP", "SGP" to "SG", "IRN" to "IR",
    "CZE" to "CZ", "POL" to "PL", "ITA" to "IT", "ESP" to "ES", "CAN" to "CA", "AUS" to "AU"
)

/**
 * Works out which country a server is in, from (in order): a flag emoji, a country or city name
 * in the remarks, an upper-case country code in the remarks, a country prefix in the host
 * (e.g. "de-n.example.com"), and finally the host's country-code domain.
 */
internal fun guessCountryIso(remarks: String, host: String?, data: WorldMapData?): String? {
    findFlagEmoji(remarks)?.let { flagEmojiToIso(it) }?.let { return it }
    if (data == null) return null

    val lower = remarks.lowercase()
    for ((regex, iso) in data.nameMatchers) {
        if (regex.containsMatchIn(lower)) return iso
    }

    for (m in Regex("(?<![A-Za-z])[A-Z]{2,3}(?![A-Za-z])").findAll(remarks)) {
        val token = m.value
        val iso = CODE_ALIASES[token] ?: token.takeIf { it.length == 2 }
        if (iso != null && data.countryPoints.containsKey(iso)) return iso
    }

    if (!host.isNullOrBlank()) {
        val labels = host.lowercase().split('.')
        val prefix = labels.first().split('-', '_').first().trimEnd { it.isDigit() }.uppercase()
        val prefixIso = CODE_ALIASES[prefix] ?: prefix.takeIf { it.length == 2 }
        if (prefixIso != null && labels.size > 1 && data.countryPoints.containsKey(prefixIso)) return prefixIso

        val tld = labels.last().uppercase()
        val tldIso = if (tld == "UK") "GB" else tld
        if (tldIso.length == 2 && data.countryPoints.containsKey(tldIso)) return tldIso
    }
    return null
}

private class MapCamera(val cx: Float, val cy: Float, val scale: Float)

private fun fitCamera(points: List<Offset>, windowW: Float, windowH: Float): MapCamera {
    val w = if (windowW > 0f) windowW else 1f
    val h = if (windowH > 0f) windowH else 1f
    if (points.isEmpty()) {
        return MapCamera(
            cx = WorldMapProjection.x(48f),
            cy = WorldMapProjection.y(34f),
            scale = minOf(w / 150f, h / 110f)
        )
    }
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val spanW = max((maxX - minX) * 1.7f, 60f)
    val spanH = max((maxY - minY) * 2.2f, 45f)
    return MapCamera(
        cx = (minX + maxX) / 2f,
        cy = (minY + maxY) / 2f,
        scale = minOf(w / spanW, h / spanH)
    )
}

@Composable
internal fun WorldMapView(
    map: WorldMapData?,
    origin: Offset?,
    destination: Offset?,
    originLabel: String?,
    destinationLabel: String?,
    connected: Boolean,
    windowTop: Float,
    windowBottom: Float,
    accent: Color,
    originColor: Color,
    outlineColor: Color,
    landColor: Color,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val points = listOfNotNull(origin, destination)
    val camera = remember(origin, destination, canvasSize, windowTop, windowBottom) {
        fitCamera(points, canvasSize.width.toFloat(), windowBottom - windowTop)
    }
    val cx by animateFloatAsState(camera.cx, tween(1100, easing = FastOutSlowInEasing), label = "mapCx")
    val cy by animateFloatAsState(camera.cy, tween(1100, easing = FastOutSlowInEasing), label = "mapCy")
    val zoom by animateFloatAsState(camera.scale, tween(1100, easing = FastOutSlowInEasing), label = "mapZoom")

    val transition = rememberInfiniteTransition(label = "mapPulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Restart),
        label = "pulse"
    )
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
    ) {
        // Layer 1: the map itself. It only reads the camera values, so it is NOT redrawn on every
        // animation frame of the pulse below (that was the main source of lag).
        Canvas(modifier = Modifier.fillMaxSize()) {
            val data = map ?: return@Canvas
            val s = zoom
            val wcx = this.size.width / 2f
            val wcy = (windowTop + windowBottom) / 2f
            withTransform({
                translate(left = wcx - cx * s, top = wcy - cy * s)
                scale(scaleX = s, scaleY = s, pivot = Offset.Zero)
            }) {
                drawPath(data.path, color = landColor)
                drawPath(data.path, color = outlineColor, style = Stroke(width = 1.dp.toPx() / s))
            }
        }

        // Layer 2: route, markers and labels (cheap shapes, redrawn every frame by the pulse).
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = zoom
            val wcx = this.size.width / 2f
            val wcy = (windowTop + windowBottom) / 2f
            fun toScreen(p: Offset) = Offset(wcx + (p.x - cx) * s, wcy + (p.y - cy) * s)

            val o = origin?.let { toScreen(it) }
            val d = destination?.let { toScreen(it) }

            if (connected && o != null && d != null) {
                val dist = hypot(d.x - o.x, d.y - o.y)
                val ctrl = Offset((o.x + d.x) / 2f, (o.y + d.y) / 2f - dist * 0.28f)
                val route = Path().apply {
                    moveTo(o.x, o.y)
                    cubicTo(
                        o.x + (ctrl.x - o.x) * 2f / 3f, o.y + (ctrl.y - o.y) * 2f / 3f,
                        d.x + (ctrl.x - d.x) * 2f / 3f, d.y + (ctrl.y - d.y) * 2f / 3f,
                        d.x, d.y
                    )
                }
                drawPath(route, accent.copy(alpha = 0.10f), style = Stroke(16.dp.toPx(), cap = StrokeCap.Round))
                drawPath(route, accent.copy(alpha = 0.22f), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                drawPath(route, accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))

                val measure = PathMeasure()
                measure.setPath(route, false)
                val len = measure.length
                if (len > 0f) {
                    for (t in listOf(0.28f, 0.52f, 0.76f)) {
                        val p = measure.getPosition(len * t)
                        drawCircle(accent.copy(alpha = 0.35f), 8.dp.toPx(), p)
                        drawCircle(Color.White.copy(alpha = 0.9f), 3.5.dp.toPx(), p)
                    }
                    val moving = measure.getPosition(len * pulse)
                    drawCircle(accent.copy(alpha = 0.5f), 11.dp.toPx(), moving)
                    drawCircle(Color.White, 5.dp.toPx(), moving)
                }
            }

            if (d != null) {
                if (connected) {
                    drawCircle(accent.copy(alpha = 0.25f), 16.dp.toPx(), d)
                    drawCircle(accent, 7.dp.toPx(), d)
                    drawBrackets(d, 19.dp.toPx(), 8.dp.toPx(), Color.White.copy(alpha = 0.9f), 2.dp.toPx())
                } else {
                    drawCircle(accent.copy(alpha = 0.18f), 12.dp.toPx(), d)
                    drawCircle(accent.copy(alpha = 0.55f), 5.dp.toPx(), d)
                }
                if (!destinationLabel.isNullOrBlank()) {
                    drawMapLabel(textMeasurer, destinationLabel, Offset(d.x, d.y + 26.dp.toPx()), accent.copy(alpha = 0.22f), Color.White)
                }
            }

            if (o != null) {
                val ringRadius = 8.dp.toPx() + pulse * 20.dp.toPx()
                drawCircle(
                    originColor.copy(alpha = (1f - pulse) * 0.45f),
                    ringRadius,
                    o,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(originColor.copy(alpha = 0.25f), 14.dp.toPx(), o)
                drawCircle(originColor, 6.dp.toPx(), o)
                if (!originLabel.isNullOrBlank()) {
                    drawMapLabel(textMeasurer, originLabel, Offset(o.x, o.y + 26.dp.toPx()), originColor.copy(alpha = 0.22f), Color.White)
                }
            }
        }
    }
}

private fun DrawScope.drawMapLabel(
    measurer: TextMeasurer,
    text: String,
    anchor: Offset,
    background: Color,
    foreground: Color
) {
    val layout = measurer.measure(
        text,
        TextStyle(color = foreground, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    )
    val padX = 8.dp.toPx()
    val padY = 3.dp.toPx()
    val w = layout.size.width + padX * 2
    val h = layout.size.height + padY * 2
    val topLeft = Offset(anchor.x - w / 2f, anchor.y)
    drawRoundRect(color = Color(0xCC0A0D12), topLeft = topLeft, size = Size(w, h), cornerRadius = CornerRadius(h / 2f))
    drawRoundRect(color = background, topLeft = topLeft, size = Size(w, h), cornerRadius = CornerRadius(h / 2f))
    drawText(layout, topLeft = Offset(topLeft.x + padX, topLeft.y + padY))
}

private fun DrawScope.drawBrackets(center: Offset, half: Float, arm: Float, color: Color, strokeWidth: Float) {
    val l = center.x - half
    val r = center.x + half
    val t = center.y - half
    val b = center.y + half
    fun seg(x1: Float, y1: Float, x2: Float, y2: Float) {
        drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    }
    seg(l, t, l + arm, t); seg(l, t, l, t + arm)
    seg(r, t, r - arm, t); seg(r, t, r, t + arm)
    seg(l, b, l + arm, b); seg(l, b, l, b - arm)
    seg(r, b, r - arm, b); seg(r, b, r, b - arm)
}
