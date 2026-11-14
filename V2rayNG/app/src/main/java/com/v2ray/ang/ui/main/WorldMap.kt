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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.tan

/** World coastline/border outlines plus a country-code -> map-point table, in Mercator "map units". */
internal class WorldMapData(
    val path: Path,
    val countryPoints: Map<String, Offset>
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
    return WorldMapData(path, points)
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

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
    ) {
        val s = zoom
        val wcx = this.size.width / 2f
        val wcy = (windowTop + windowBottom) / 2f

        if (map != null) {
            withTransform({
                translate(left = wcx - cx * s, top = wcy - cy * s)
                scale(scaleX = s, scaleY = s, pivot = Offset.Zero)
            }) {
                drawPath(map.path, color = landColor)
                drawPath(map.path, color = outlineColor, style = Stroke(width = 1.dp.toPx() / s))
            }
        }

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
        }
    }
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
