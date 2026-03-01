package com.wirewhisper.ui.now

import android.app.Activity
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import com.wirewhisper.ui.util.formatBytes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun NowScreen(
    onFlowClick: (Long) -> Unit,
    viewModel: NowViewModel = viewModel(),
) {
    val isRunning by viewModel.vpnRunning.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val groupMode by viewModel.groupMode.collectAsStateWithLifecycle()
    val trafficDetail by viewModel.trafficDetail.collectAsStateWithLifecycle()
    val timeFilter by viewModel.timeFilter.collectAsStateWithLifecycle()
    val showBlockedOnly by viewModel.showBlockedOnly.collectAsStateWithLifecycle()
    val followMode by viewModel.followMode.collectAsStateWithLifecycle()
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpn()
        }
    }

    // Traffic detail bottom sheet
    trafficDetail?.let { detail ->
        TrafficBottomSheet(
            detail = detail,
            onDismiss = { viewModel.dismissTrafficDetail() },
        )
    }

    Scaffold(
        floatingActionButton = {
            if (!fullscreen) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (isRunning) {
                            viewModel.stopVpn()
                        } else {
                            val prepareIntent = viewModel.prepareVpn()
                            if (prepareIntent != null) {
                                vpnPermissionLauncher.launch(prepareIntent)
                            } else {
                                viewModel.startVpn()
                            }
                        }
                    },
                    icon = {
                        Icon(
                            if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                        )
                    },
                    text = { Text(if (isRunning) "Stop" else "Start") },
                    containerColor = if (isRunning)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary,
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!fullscreen) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Live Flows",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${state.totalActiveFlows} active",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = { viewModel.toggleFollowMode() }) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = if (followMode) "Disable follow" else "Follow mode",
                                modifier = Modifier.size(20.dp),
                                tint = if (followMode) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { fullscreen = true }) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                // Group mode toggle
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    SegmentedButton(
                        selected = groupMode == GroupMode.BY_APP,
                        onClick = { viewModel.onGroupModeChanged(GroupMode.BY_APP) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) {
                        Text("App")
                    }
                    SegmentedButton(
                        selected = groupMode == GroupMode.BY_COUNTRY,
                        onClick = { viewModel.onGroupModeChanged(GroupMode.BY_COUNTRY) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) {
                        Text("Country")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Sort mode toggle (only in BY_APP mode)
                if (groupMode == GroupMode.BY_APP) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        SegmentedButton(
                            selected = state.sortMode == SortMode.RECENT_ACTIVITY,
                            onClick = { viewModel.onSortModeChanged(SortMode.RECENT_ACTIVITY) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) {
                            Text("Recent")
                        }
                        SegmentedButton(
                            selected = state.sortMode == SortMode.TOTAL_BYTES,
                            onClick = { viewModel.onSortModeChanged(SortMode.TOTAL_BYTES) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) {
                            Text("Bytes")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = {
                        Text(
                            if (groupMode == GroupMode.BY_APP) "Filter apps..."
                            else "Filter countries..."
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                )

                Spacer(Modifier.height(8.dp))

                // Time filter + blocked filter row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    var showTimeMenu by remember { mutableStateOf(false) }
                    Box {
                        FilterChip(
                            selected = timeFilter != TimeFilter.ALL,
                            onClick = { showTimeMenu = true },
                            label = { Text(timeFilter.shortLabel) },
                            leadingIcon = {
                                Icon(Icons.Default.Schedule, null, Modifier.size(16.dp))
                            },
                        )
                        DropdownMenu(
                            expanded = showTimeMenu,
                            onDismissRequest = { showTimeMenu = false },
                        ) {
                            TimeFilter.entries.forEach { filter ->
                                DropdownMenuItem(
                                    text = { Text(filter.label) },
                                    onClick = {
                                        viewModel.setTimeFilter(filter)
                                        showTimeMenu = false
                                    },
                                    leadingIcon = if (filter == timeFilter) {
                                        { Icon(Icons.Default.Check, null) }
                                    } else null,
                                )
                            }
                        }
                    }

                    FilterChip(
                        selected = showBlockedOnly,
                        onClick = { viewModel.toggleShowBlockedOnly() },
                        label = { Text("Blocked") },
                        leadingIcon = if (showBlockedOnly) {
                            { Icon(Icons.Default.Block, null, Modifier.size(16.dp)) }
                        } else null,
                    )
                }

                Spacer(Modifier.height(8.dp))
            } else {
                // Fullscreen: minimal header with exit button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${state.totalActiveFlows} active",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    IconButton(onClick = { fullscreen = false }) {
                        Icon(
                            Icons.Default.FullscreenExit,
                            contentDescription = "Exit fullscreen",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            val isEmpty = when (groupMode) {
                GroupMode.BY_APP -> state.appGroups.isEmpty()
                GroupMode.BY_COUNTRY -> state.countryGroups.isEmpty()
            }

            if (isEmpty && !isRunning) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Tap Start to begin monitoring",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                when (groupMode) {
                    GroupMode.BY_APP -> {
                        val listState = rememberLazyListState()

                        // Detect scroll for sort pause/resume + disable follow on manual scroll
                        LaunchedEffect(listState.isScrollInProgress) {
                            if (listState.isScrollInProgress) {
                                viewModel.onUserInteractionStarted()
                                // Disable follow mode when user manually scrolls away from top
                                if (followMode && listState.firstVisibleItemIndex > 0) {
                                    viewModel.disableFollowMode()
                                }
                            } else {
                                viewModel.onUserInteractionEnded()
                            }
                        }

                        // Follow mode: auto-scroll to top when data changes
                        LaunchedEffect(state.appGroups, followMode) {
                            if (followMode && state.appGroups.isNotEmpty()
                                && listState.firstVisibleItemIndex > 0) {
                                listState.animateScrollToItem(0)
                            }
                        }

                        LazyColumn(state = listState) {
                            items(
                                items = state.appGroups,
                                key = { it.uid },
                            ) { group ->
                                AppGroupItem(
                                    group = group,
                                    onToggle = {
                                        viewModel.onUserInteractionStarted()
                                        viewModel.toggleAppExpansion(group.uid)
                                        viewModel.onUserInteractionEnded()
                                    },
                                    onSparklineClick = { viewModel.showTrafficDetail(group.uid) },
                                    onToggleBlock = { viewModel.toggleAppBlock(group.packageName ?: return@AppGroupItem) },
                                    onToggleHostnameBlock = { hostname ->
                                        viewModel.toggleHostnameBlock(group.packageName ?: return@AppGroupItem, hostname)
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                    GroupMode.BY_COUNTRY -> {
                        LazyColumn {
                            items(
                                items = state.countryGroups,
                                key = { it.countryCode },
                            ) { group ->
                                CountryGroupItem(
                                    group = group,
                                    onToggleCountry = { viewModel.toggleCountryExpansion(group.countryCode) },
                                    onToggleCountryBlock = { viewModel.toggleCountryBlock(group.countryCode) },
                                    onToggleApp = { uid -> viewModel.toggleCountryAppExpansion(group.countryCode, uid) },
                                    onSparklineClick = { uid -> viewModel.showTrafficDetail(uid) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// region App grouping composables

@Composable
private fun AppGroupItem(
    group: AppGroupUiModel,
    onToggle: () -> Unit,
    onSparklineClick: () -> Unit,
    onToggleBlock: () -> Unit,
    onToggleHostnameBlock: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column {
            // App row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .alpha(if (group.isBlocked) 0.5f else 1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // App icon
                val bitmap = rememberDrawableBitmap(group.icon)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = group.appName,
                        modifier = Modifier.size(32.dp),
                    )
                } else {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.appName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${group.hostnames.size} destination${if (group.hostnames.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                TrafficSparkline(
                    samples = group.sparklineSamples,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clickable(onClick = onSparklineClick),
                )

                Text(
                    text = formatBytes(group.totalBytes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Block toggle with shake animation
                if (group.packageName != null) {
                    ShakingBlockIcon(
                        isBlocked = group.isBlocked,
                        blockedAttemptCount = group.blockedAttemptCount,
                        onClick = onToggleBlock,
                    )
                } else {
                    Spacer(Modifier.width(4.dp))
                }

                Icon(
                    if (group.isExpanded) Icons.Default.KeyboardArrowDown
                    else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (group.isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Expanded hostname rows
            AnimatedVisibility(
                visible = group.isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    group.hostnames.forEach { hostname ->
                        HostnameRow(
                            model = hostname,
                            onToggleBlock = if (group.packageName != null && !group.isBlocked) {
                                { onToggleHostnameBlock(hostname.hostname) }
                            } else null,
                        )
                    }
                }
            }
        }
    }
}

// endregion

// region Country grouping composables

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CountryGroupItem(
    group: CountryGroupUiModel,
    onToggleCountry: () -> Unit,
    onToggleCountryBlock: () -> Unit,
    onToggleApp: (Int) -> Unit,
    onSparklineClick: (Int) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = onToggleCountry,
                onLongClick = onToggleCountryBlock,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column {
            // Country header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .alpha(if (group.isBlocked) 0.5f else 1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = group.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${group.apps.size} app${if (group.apps.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatBytes(group.totalBytes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ShakingBlockIcon(
                    isBlocked = group.isBlocked,
                    blockedAttemptCount = group.blockedAttemptCount,
                    onClick = onToggleCountryBlock,
                    iconSize = 16,
                    buttonSize = 28,
                )

                Icon(
                    if (group.isExpanded) Icons.Default.KeyboardArrowDown
                    else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (group.isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Expanded app rows
            AnimatedVisibility(
                visible = group.isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    group.apps.forEach { appModel ->
                        CountryAppRow(
                            app = appModel,
                            onToggle = { onToggleApp(appModel.uid) },
                            onSparklineClick = { onSparklineClick(appModel.uid) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryAppRow(
    app: CountryAppUiModel,
    onToggle: () -> Unit,
    onSparklineClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = 24.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val bitmap = rememberDrawableBitmap(app.icon)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = app.appName,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Icon(
                    Icons.Default.Language,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))

            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            TrafficSparkline(
                samples = app.sparklineSamples,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clickable(onClick = onSparklineClick),
            )

            Text(
                text = formatBytes(app.totalBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                if (app.isExpanded) Icons.Default.KeyboardArrowDown
                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (app.isExpanded) "Collapse" else "Expand",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Expanded hostname rows within the app
        AnimatedVisibility(
            visible = app.isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                app.hostnames.forEach { hostname ->
                    HostnameRow(hostname, indentDp = 60)
                }
            }
        }
    }
}

// endregion

// region Shared composables

/** Block icon with damped shake animation on new blocked attempts. */
@Composable
private fun ShakingBlockIcon(
    isBlocked: Boolean,
    blockedAttemptCount: Long,
    onClick: () -> Unit,
    iconSize: Int = 18,
    buttonSize: Int = 32,
) {
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(blockedAttemptCount) {
        if (blockedAttemptCount > 0 && isBlocked) {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    (-4f) at 50
                    4f at 100
                    (-3f) at 150
                    3f at 200
                    (-2f) at 250
                    2f at 300
                    0f at 400
                }
            )
        }
    }

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(buttonSize.dp)
            .offset { IntOffset(shakeOffset.value.toInt(), 0) },
    ) {
        Icon(
            imageVector = if (isBlocked) Icons.Default.Block else Icons.Default.CheckCircle,
            contentDescription = if (isBlocked) "Unblock" else "Block",
            modifier = Modifier.size(iconSize.dp),
            tint = if (isBlocked) MaterialTheme.colorScheme.error
            else Color(0xFF4CAF50),
        )
    }
}

@Composable
private fun HostnameRow(
    model: HostnameGroupUiModel,
    indentDp: Int = 48,
    onToggleBlock: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentDp.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
            .alpha(if (model.isBlocked || model.parentAppBlocked) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Language,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = model.hostname,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        if (model.flowCount > 1) {
            Text(
                text = "${model.flowCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = formatBytes(model.totalBytes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onToggleBlock != null) {
            ShakingBlockIcon(
                isBlocked = model.isBlocked || model.parentAppBlocked,
                blockedAttemptCount = model.blockedAttemptCount,
                onClick = onToggleBlock,
                iconSize = 14,
                buttonSize = 24,
            )
        }
    }
}

@Composable
private fun rememberDrawableBitmap(drawable: Drawable?): ImageBitmap? {
    return remember(drawable) {
        drawable?.toBitmap(64, 64)?.asImageBitmap()
    }
}

// endregion
