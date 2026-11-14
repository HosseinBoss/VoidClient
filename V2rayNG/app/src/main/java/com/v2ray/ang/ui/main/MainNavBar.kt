package com.v2ray.ang.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.colorBrandAccent

enum class MainTab(val label: String, @DrawableRes val icon: Int) {
    Home("Home", R.drawable.ic_vt_home),
    Servers("Servers", R.drawable.ic_vt_globe),
    Stats("Stats", R.drawable.ic_vt_chart),
    Settings("Settings", R.drawable.ic_settings_24dp)
}

/** Floating bottom navigation pill (Home / Servers / Stats / Settings). */
@Composable
fun MainNavBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit
) {
    val barShape = RoundedCornerShape(30.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(barShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, barShape)
                .padding(6.dp)
        ) {
            MainTab.values().forEach { tab ->
                val isSelected = tab == selected
                val itemShape = RoundedCornerShape(24.dp)
                val tint = if (isSelected) colorBrandAccent else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(itemShape)
                        .background(if (isSelected) colorBrandAccent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer)
                        .then(
                            if (isSelected) Modifier.border(1.dp, colorBrandAccent.copy(alpha = 0.7f), itemShape)
                            else Modifier
                        )
                        .clickable { onSelect(tab) }
                        .padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(tab.icon),
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = tint,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }
    }
}
