package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.compose.colorBrandAccent
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.colorPingRed
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/** Data usage reported by a subscription (the "Subscription-Userinfo" response header). */
@Immutable
data class SubscriptionUsage(
    val used: Long,
    val total: Long,
    val expireSec: Long,
    val fetchedAt: Long
) {
    val hasTotal: Boolean get() = total > 0L
    val remaining: Long get() = (total - used).coerceAtLeast(0L)
}

internal object SubscriptionUsageRepo {
    private const val KEY_PREFIX = "vt_sub_usage_"
    private const val STALE_MS = 5 * 60 * 1000L

    fun cached(subId: String): SubscriptionUsage? {
        val raw = MmkvManager.decodeSettingsString(KEY_PREFIX + subId) ?: return null
        val p = raw.split('|')
        if (p.size != 4) return null
        return SubscriptionUsage(
            used = p[0].toLongOrNull() ?: return null,
            total = p[1].toLongOrNull() ?: return null,
            expireSec = p[2].toLongOrNull() ?: return null,
            fetchedAt = p[3].toLongOrNull() ?: return null
        )
    }

    fun isStale(usage: SubscriptionUsage?): Boolean =
        usage == null || System.currentTimeMillis() - usage.fetchedAt > STALE_MS

    /** Blocking network call: run it off the main thread. Returns null when the server sends no usage info. */
    fun fetch(subId: String): SubscriptionUsage? {
        val sub = MmkvManager.decodeSubscription(subId) ?: return null
        val url = sub.url.trim()
        if (url.isEmpty()) return null

        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        val userAgent = sub.userAgent?.takeIf { it.isNotBlank() } ?: "v2rayNG/${BuildConfig.VERSION_NAME}"
        val builder = Request.Builder().url(url).get().header("User-Agent", userAgent)
        for ((key, value) in JsonUtil.parseHeadersToMap(sub.requestHeaders)) {
            try {
                builder.header(key, value)
            } catch (_: IllegalArgumentException) {
            }
        }

        return client.newCall(builder.build()).execute().use { response ->
            val header = response.header("subscription-userinfo") ?: return@use null
            val usage = parse(header) ?: return@use null
            MmkvManager.encodeSettings(
                KEY_PREFIX + subId,
                "${usage.used}|${usage.total}|${usage.expireSec}|${usage.fetchedAt}"
            )
            usage
        }
    }

    private fun parse(header: String): SubscriptionUsage? {
        val values = HashMap<String, Long>()
        header.split(';').forEach { part ->
            val kv = part.trim().split('=')
            if (kv.size == 2) {
                val number = kv[1].trim().toLongOrNull() ?: kv[1].trim().toDoubleOrNull()?.toLong()
                if (number != null) values[kv[0].trim().lowercase()] = number
            }
        }
        if (values.isEmpty()) return null
        val used = (values["upload"] ?: 0L) + (values["download"] ?: 0L)
        return SubscriptionUsage(
            used = used,
            total = values["total"] ?: 0L,
            expireSec = values["expire"] ?: 0L,
            fetchedAt = System.currentTimeMillis()
        )
    }
}

/** Cached usage first, then a refresh from the network if the cache is older than five minutes. */
@Composable
internal fun rememberSubscriptionUsage(subId: String?, refreshKey: Any?): State<SubscriptionUsage?> {
    return produceState<SubscriptionUsage?>(null, subId, refreshKey) {
        if (subId.isNullOrBlank()) {
            value = null
            return@produceState
        }
        val cached = withContext(Dispatchers.IO) { SubscriptionUsageRepo.cached(subId) }
        value = cached
        if (SubscriptionUsageRepo.isStale(cached)) {
            val fresh = withContext(Dispatchers.IO) { runCatching { SubscriptionUsageRepo.fetch(subId) }.getOrNull() }
            if (fresh != null) value = fresh
        }
    }
}

private fun quotaText(bytes: Long): String {
    val (value, unit) = formatBytes(bytes)
    return "$value $unit"
}

private fun expiryText(expireSec: Long): String {
    if (expireSec <= 0L) return "No expiry"
    val days = ceil((expireSec * 1000L - System.currentTimeMillis()) / 86_400_000.0).toLong()
    return when {
        days < 0L -> "Expired"
        days == 0L -> "Expires today"
        days == 1L -> "1 day left"
        else -> "$days days left"
    }
}

/** Remaining volume, expiry and a usage bar. */
@Composable
internal fun SubscriptionUsageBar(usage: SubscriptionUsage, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val fraction = if (usage.hasTotal) (usage.used.toFloat() / usage.total.toFloat()).coerceIn(0f, 1f) else 0f
    val barColor: Color = when {
        fraction >= 0.95f -> colorPingRed
        fraction >= 0.8f -> colorConfigType
        else -> colorBrandAccent
    }
    val expired = usage.expireSec > 0L && usage.expireSec * 1000L < System.currentTimeMillis()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainerHigh)
            .border(1.dp, scheme.outlineVariant, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (usage.hasTotal) "${quotaText(usage.remaining)} left of ${quotaText(usage.total)}"
                else "Used ${quotaText(usage.used)} \u2022 Unlimited",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = expiryText(usage.expireSec),
                fontSize = 12.sp,
                color = if (expired) colorPingRed else scheme.onSurfaceVariant
            )
        }
        if (usage.hasTotal) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(scheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(CircleShape)
                        .background(barColor)
                )
            }
        }
    }
}
