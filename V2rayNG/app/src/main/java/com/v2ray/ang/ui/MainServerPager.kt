package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.DeviceLock
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.compose.ReorderableGridItem
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.colorBrandAccent
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed
import com.v2ray.ang.ui.compose.verticalScrollbar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs

@Composable
fun GroupPagerPage(
    groupId: String,
    mainViewModel: MainViewModel,
    selectedGuid: String?,
    doubleColumnDisplay: Boolean,
    confirmRemove: Boolean,
    searchQuery: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit,
    contentPadding: PaddingValues
) {
    val serverFlow = remember(groupId) {
        mainViewModel.serversForGroup(groupId)
    }
    val servers by serverFlow.collectAsStateWithLifecycle()
    var selectedFilter by remember(groupId) { mutableStateOf(ServerFilter.All) }
    val canReorder = groupId.isNotEmpty() && searchQuery.isEmpty() && selectedFilter == ServerFilter.All
    val filteredServers = remember(servers, selectedFilter) {
        applyServerFilter(servers, selectedFilter)
    }
    Column(Modifier.fillMaxSize()) {
    ServerFilterChips(
        selected = selectedFilter,
        onSelect = { selectedFilter = it }
    )
    Box(Modifier.weight(1f).fillMaxWidth()) {
    ServerListPage(
        servers = filteredServers,
        selectedGuid = selectedGuid,
        canReorder = canReorder,
        doubleColumnDisplay = doubleColumnDisplay,
        subscriptionId = groupId,
        confirmRemove = confirmRemove,
        groupId = groupId,
        lazyListStates = lazyListStates,
        lazyGridStates = lazyGridStates,
        onSelectServer = onSelectServer,
        onEditServer = onEditServer,
        onShareServer = onShareServer,
        onMoreServer = onMoreServer,
        onRemoveServer = onRemoveServer,
        onMoveServer = { fromIndex, toIndex -> mainViewModel.moveServer(groupId, fromIndex, toIndex) },
        contentPadding = contentPadding
    )
    }
    }
}

@Composable
private fun ServerListPage(
    servers: List<ServersCache>,
    selectedGuid: String?,
    canReorder: Boolean,
    doubleColumnDisplay: Boolean,
    subscriptionId: String,
    confirmRemove: Boolean,
    groupId: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit,
    onMoveServer: (Int, Int) -> Unit,
    contentPadding: PaddingValues
) {
    if (doubleColumnDisplay) {
        val gridState = remember(groupId) {
            lazyGridStates.getOrPut(groupId) { LazyGridState() }
        }
        val reorderableGridState = if (canReorder) {
            rememberReorderableLazyGridState(gridState) { from, to ->
                onMoveServer(from.index, to.index)
            }
        } else null

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(gridState),
            contentPadding = contentPadding
        ) {
            itemsIndexed(items = servers, key = { _, item -> item.guid }) { _, serverCache ->
                val content: @Composable () -> Unit = {
                    ServerItemColumn(
                        serverCache = serverCache,
                        selectedGuid = selectedGuid,
                        subscriptionId = subscriptionId,
                        doubleColumnDisplay = true,
                        onSelectServer = onSelectServer,
                        onEditServer = onEditServer,
                        onShareServer = onShareServer,
                        onMoreServer = onMoreServer,
                        onRemoveServer = onRemoveServer
                    )
                }
                if (canReorder && reorderableGridState != null) {
                    ReorderableItem(
                        reorderableGridState,
                        key = serverCache.guid
                    ) { isDragging ->
                        ReorderableGridItem(
                            scope = this,
                            isDragging = isDragging
                        ) { content() }
                    }
                } else {
                    content()
                }
            }
        }
    } else {
        val listState = remember(groupId) {
            lazyListStates.getOrPut(groupId) { LazyListState() }
        }
        val reorderableState = if (canReorder) {
            rememberReorderableLazyListState(listState) { from, to ->
                onMoveServer(from.index, to.index)
            }
        } else null

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(listState),
            contentPadding = contentPadding
        ) {
            itemsIndexed(items = servers, key = { _, item -> item.guid }) { _, serverCache ->
                if (canReorder && reorderableState != null) {
                    ReorderableItem(
                        reorderableState,
                        key = serverCache.guid
                    ) { isDragging ->
                        ReorderableListItem(
                            scope = this,
                            isDragging = isDragging
                        ) {
                            ServerItemRow(
                                serverCache = serverCache,
                                selectedGuid = selectedGuid,
                                subscriptionId = subscriptionId,
                                onSelectServer = onSelectServer,
                                onEditServer = onEditServer,
                                onShareServer = onShareServer,
                                onMoreServer = onMoreServer,
                                onRemoveServer = onRemoveServer
                            )
                        }
                    }
                } else {
                    ServerItemRow(
                        serverCache = serverCache,
                        selectedGuid = selectedGuid,
                        subscriptionId = subscriptionId,
                        onSelectServer = onSelectServer,
                        onEditServer = onEditServer,
                        onShareServer = onShareServer,
                        onMoreServer = onMoreServer,
                        onRemoveServer = onRemoveServer
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerItemRow(
    serverCache: ServersCache,
    selectedGuid: String?,
    subscriptionId: String,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit
) {
    val profile = serverCache.profile
    val context = LocalContext.current
    val locked = DeviceLock.isLockedRemarks(profile.remarks)
    val subRemarks = if (subscriptionId.isEmpty()) {
        MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks?.firstOrNull()
            ?.toString() ?: ""
    } else ""

    ServerListItem(
        remarks = profile.remarks,
        statistics = profile.description.nullIfBlank()
            ?: AngConfigManager.generateDescription(profile),
        typeDescription = getProtocolDescription(profile),
        testResult = serverCache.testDelayString,
        testDelayMillis = serverCache.testDelayMillis,
        isSelected = serverCache.guid == selectedGuid,
        subscriptionRemarks = subRemarks,
        doubleColumnDisplay = false,
        onClick = { onSelectServer(serverCache.guid) },
        onShare = { if (locked) lockedToast(context) else onShareServer(serverCache.guid, profile) },
        onEdit = { if (locked) lockedToast(context) else onEditServer(serverCache.guid, profile) },
        onRemove = { onRemoveServer(serverCache.guid) },
        onMore = { if (locked) lockedToast(context) else onMoreServer(serverCache.guid, profile) },
        locked = locked
    )
}

@Composable
private fun ServerItemColumn(
    serverCache: ServersCache,
    selectedGuid: String?,
    subscriptionId: String,
    doubleColumnDisplay: Boolean,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit
) {
    val profile = serverCache.profile
    val context = LocalContext.current
    val locked = DeviceLock.isLockedRemarks(profile.remarks)
    val subRemarks = if (subscriptionId.isEmpty()) {
        MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks?.firstOrNull()?.toString() ?: ""
    } else ""
    ServerListItem(
        remarks = profile.remarks,
        statistics = profile.description.nullIfBlank() ?: AngConfigManager.generateDescription(profile),
        typeDescription = getProtocolDescription(profile),
        testResult = serverCache.testDelayString,
        testDelayMillis = serverCache.testDelayMillis,
        isSelected = serverCache.guid == selectedGuid,
        subscriptionRemarks = subRemarks,
        doubleColumnDisplay = doubleColumnDisplay,
        onClick = { onSelectServer(serverCache.guid) },
        onEdit = { if (locked) lockedToast(context) else onEditServer(serverCache.guid, profile) },
        onShare = { if (locked) lockedToast(context) else onShareServer(serverCache.guid, profile) },
        onRemove = { onRemoveServer(serverCache.guid) },
        onMore = { if (locked) lockedToast(context) else onMoreServer(serverCache.guid, profile) },
        locked = locked
    )
}

private fun lockedToast(context: Context) {
    DeviceLock.toast(context, "This config is locked to this device and can't be shared or edited")
}

private fun leadingFlagEmoji(remarks: String): String? {
    if (remarks.length < 2) return null
    val codePoints = remarks.codePoints().toArray()
    if (codePoints.size < 2) return null
    val isRegionalIndicator = { cp: Int -> cp in 0x1F1E6..0x1F1FF }
    return if (isRegionalIndicator(codePoints[0]) && isRegionalIndicator(codePoints[1])) {
        String(codePoints, 0, 2)
    } else {
        null
    }
}

private fun pingQualityLabel(testDelayMillis: Long): String? = when {
    testDelayMillis <= 0L -> null // untested or timeout is already conveyed by the result text
    testDelayMillis < 150L -> "Excellent"
    testDelayMillis < 400L -> "Good"
    else -> "Poor"
}

@Composable
fun ServerListItem(
    remarks: String,
    statistics: String,
    typeDescription: String,
    testResult: String,
    testDelayMillis: Long,
    isSelected: Boolean,
    subscriptionRemarks: String,
    doubleColumnDisplay: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
    locked: Boolean = false
) {
    val cardShape = RoundedCornerShape(18.dp)
    val cardBackground = if (isSelected) {
        colorBrandAccent.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val flagEmoji = remember(remarks) { leadingFlagEmoji(remarks) }
    val displayTitle = remember(remarks, flagEmoji) {
        if (flagEmoji != null) remarks.removePrefix(flagEmoji).trimStart() else remarks
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .then(dragModifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(cardShape)
                .background(cardBackground)
                .then(
                    if (isSelected) Modifier.border(1.5.dp, colorBrandAccent, cardShape)
                    else Modifier
                )
                .clickable(onClick = onClick)
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (flagEmoji != null) {
                        Text(flagEmoji, fontSize = 18.sp, modifier = Modifier.padding(end = 6.dp))
                    }
                    if (locked) {
                        Text("\uD83D\uDD12", fontSize = 14.sp, modifier = Modifier.padding(end = 4.dp))
                    }
                    Text(displayTitle, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Paragraph), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (doubleColumnDisplay) {
                        IconButton(onClick = onMore, Modifier.size(36.dp)) {
                            Icon(painterResource(R.drawable.ic_more_vert_24dp), null, Modifier.size(24.dp))
                        }
                    } else {
                        IconButton(onClick = onShare, Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_share_24dp), null, Modifier.size(24.dp)) }
                        IconButton(onClick = onEdit, Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_edit_24dp), null, Modifier.size(24.dp)) }
                        IconButton(onClick = onRemove, Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_delete_24dp), null, Modifier.size(24.dp)) }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (subscriptionRemarks.isNotBlank()) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(colorBrandAccent.copy(alpha = 0.2f)), Alignment.Center
                        ) {
                            Text(subscriptionRemarks.take(1).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colorBrandAccent)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(statistics, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorConfigType.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(typeDescription, style = MaterialTheme.typography.bodySmall, color = colorConfigType, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val pingColor = if (testDelayMillis < 0L) colorPingRed else colorPing
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(pingColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(testResult, style = MaterialTheme.typography.bodySmall, color = pingColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        pingQualityLabel(testDelayMillis)?.let { quality ->
                            Text(" \u00b7 $quality", style = MaterialTheme.typography.bodySmall, color = pingColor.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun getProtocolDescription(profile: ProfileItem): String {
    if (profile.configType.isComplexType()) return profile.configType.name
    val parts = mutableListOf(profile.configType.name)
    profile.network?.let { net ->
        if (net.isNotBlank() && !net.equals("tcp", ignoreCase = true)) parts.add(net)
    }
    profile.security?.let { sec ->
        if (sec.isNotBlank()) {
            if (profile.insecure == true && sec.equals("tls", ignoreCase = true)) {
                parts.add("$sec insecure")
            } else {
                parts.add(sec)
            }
        }
    }
    return parts.joinToString(" / ")
}

private enum class ServerFilter(val label: String) {
    All("All"),
    Fastest("Fastest"),
    Tls("TLS"),
    Reality("Reality"),
    Other("Other")
}

private fun applyServerFilter(servers: List<ServersCache>, filter: ServerFilter): List<ServersCache> {
    fun type(s: ServersCache) = getProtocolDescription(s.profile).lowercase()
    return when (filter) {
        ServerFilter.All -> servers
        ServerFilter.Fastest -> servers.filter { it.testDelayMillis > 0L }.sortedBy { it.testDelayMillis }
        ServerFilter.Tls -> servers.filter { type(it).contains("tls") }
        ServerFilter.Reality -> servers.filter { type(it).contains("reality") }
        ServerFilter.Other -> servers.filter { !type(it).contains("tls") && !type(it).contains("reality") }
    }
}

@Composable
private fun ServerFilterChips(
    selected: ServerFilter,
    onSelect: (ServerFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ServerFilter.values().forEach { filter ->
            val isSelected = filter == selected
            Text(
                text = filter.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) colorBrandAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) colorBrandAccent.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .then(
                        if (isSelected) Modifier.border(1.dp, colorBrandAccent, RoundedCornerShape(20.dp))
                        else Modifier
                    )
                    .clickable { onSelect(filter) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

internal suspend fun PagerState.navigateToPageOptimized(
    targetPage: Int,
    animateAdjacentPage: Boolean = true
) {
    if (pageCount <= 0) return
    val target = targetPage.coerceIn(0, pageCount - 1)
    val current = settledPage.coerceIn(0, pageCount - 1)
    if (target == current) return

    if (abs(target - current) == 1 && animateAdjacentPage) {
        animateScrollToPage(target)
    } else {
        scrollToPage(target)
    }
}
