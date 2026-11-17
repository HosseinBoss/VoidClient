package com.v2ray.ang.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.compose.colorBrandAccent
import com.v2ray.ang.ui.compose.colorConfigType

@Composable
fun StatsScreen(
    isRunning: Boolean,
    selectedGuid: String?,
    traffic: TrafficSession,
    contentPadding: PaddingValues,
    onImportConfig: (String) -> Unit
) {
    val subId = remember(selectedGuid) {
        selectedGuid?.let { MmkvManager.decodeServerConfig(it)?.subscriptionId }?.takeIf { it.isNotBlank() }
    }
    val usage by rememberSubscriptionUsage(subId, isRunning)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Stats", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            if (isRunning) "Current connection" else "Not connected",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        usage?.let {
            Text("SUBSCRIPTION", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SubscriptionUsageBar(it)
        }

        val down = formatBytes(traffic.rxBytes)
        val up = formatBytes(traffic.txBytes)
        StatCard(R.drawable.ic_vt_arrow_down, "DOWNLOAD", down.first, down.second, formatSpeed(traffic.rxSpeed), colorBrandAccent)
        StatCard(R.drawable.ic_vt_arrow_up, "UPLOAD", up.first, up.second, formatSpeed(traffic.txSpeed), colorConfigType)

        val shape = RoundedCornerShape(18.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("DURATION", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(
                if (isRunning) formatDuration(traffic.elapsedSec) else "00:00:00",
                fontFamily = FontFamily.Monospace,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        DeviceLockSection(selectedGuid = selectedGuid, onImportConfig = onImportConfig)

        Text(
            if (traffic.supported) {
                "Numbers cover this connection only and include protocol overhead, so they are approximate."
            } else {
                "This device does not report per-app traffic, so usage cannot be shown."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatCard(
    @DrawableRes icon: Int,
    label: String,
    value: String,
    unit: String,
    speed: String,
    tint: Color
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, Modifier.size(16.dp), tint = tint)
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tint)
            Spacer(Modifier.weight(1f))
            Text(speed, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
            Text(value, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text(unit, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 5.dp))
        }
    }
}
