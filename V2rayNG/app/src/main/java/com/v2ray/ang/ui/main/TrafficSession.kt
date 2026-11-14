package com.v2ray.ang.ui.main

import android.net.TrafficStats
import android.os.Process
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Live traffic numbers for the current connection, measured from this app's own network usage
 * (the VPN core runs under the same UID, so its outer connection to the server is what we count).
 * The numbers therefore include protocol overhead and are approximate.
 */
@Stable
class TrafficSession {
    var supported by mutableStateOf(true)
    var elapsedSec by mutableLongStateOf(0L)
    var rxBytes by mutableLongStateOf(0L)
    var txBytes by mutableLongStateOf(0L)
    var rxSpeed by mutableFloatStateOf(0f)
    var txSpeed by mutableFloatStateOf(0f)
    val rxHistory = mutableStateListOf<Float>()
    val txHistory = mutableStateListOf<Float>()

    fun reset() {
        elapsedSec = 0L
        rxBytes = 0L
        txBytes = 0L
        rxSpeed = 0f
        txSpeed = 0f
        rxHistory.clear()
        txHistory.clear()
    }

    fun push(rx: Float, tx: Float) {
        rxHistory.add(rx)
        txHistory.add(tx)
        while (rxHistory.size > MAX_SAMPLES) rxHistory.removeAt(0)
        while (txHistory.size > MAX_SAMPLES) txHistory.removeAt(0)
    }

    private companion object {
        const val MAX_SAMPLES = 60
    }
}

@Composable
fun rememberTrafficSession(isRunning: Boolean): TrafficSession {
    val session = remember { TrafficSession() }
    LaunchedEffect(isRunning) {
        session.reset()
        if (!isRunning) return@LaunchedEffect

        val uid = Process.myUid()
        val baseRx = TrafficStats.getUidRxBytes(uid)
        val baseTx = TrafficStats.getUidTxBytes(uid)
        if (baseRx < 0L || baseTx < 0L) {
            session.supported = false
            return@LaunchedEffect
        }
        session.supported = true

        var lastRx = baseRx
        var lastTx = baseTx
        val startedAt = SystemClock.elapsedRealtime()
        var lastTick = startedAt
        while (true) {
            delay(1000L)
            val now = SystemClock.elapsedRealtime()
            val rx = TrafficStats.getUidRxBytes(uid)
            val tx = TrafficStats.getUidTxBytes(uid)
            session.elapsedSec = (now - startedAt) / 1000L
            if (rx >= 0L && tx >= 0L) {
                val dt = ((now - lastTick).coerceAtLeast(1L)) / 1000f
                val dRx = (rx - lastRx).coerceAtLeast(0L)
                val dTx = (tx - lastTx).coerceAtLeast(0L)
                session.rxBytes = (rx - baseRx).coerceAtLeast(0L)
                session.txBytes = (tx - baseTx).coerceAtLeast(0L)
                session.rxSpeed = dRx / dt
                session.txSpeed = dTx / dt
                session.push(session.rxSpeed, session.txSpeed)
                lastRx = rx
                lastTx = tx
            }
            lastTick = now
        }
    }
    return session
}

/** Returns (number, unit) such as ("2.9", "MB"). */
internal fun formatBytes(bytes: Long): Pair<String, String> {
    val b = bytes.coerceAtLeast(0L).toDouble()
    return when {
        b < 1024.0 -> Pair(b.toLong().toString(), "B")
        b < 1024.0 * 1024.0 -> Pair(String.format(Locale.US, "%.1f", b / 1024.0), "KB")
        b < 1024.0 * 1024.0 * 1024.0 -> Pair(String.format(Locale.US, "%.1f", b / (1024.0 * 1024.0)), "MB")
        else -> Pair(String.format(Locale.US, "%.2f", b / (1024.0 * 1024.0 * 1024.0)), "GB")
    }
}

internal fun formatSpeed(bytesPerSecond: Float): String {
    val (value, unit) = formatBytes(bytesPerSecond.toLong())
    return "$value $unit/s"
}

internal fun formatDuration(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}
