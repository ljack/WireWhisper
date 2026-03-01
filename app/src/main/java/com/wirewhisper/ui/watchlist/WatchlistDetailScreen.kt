package com.wirewhisper.ui.watchlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wirewhisper.core.model.FlowRecord
import com.wirewhisper.ui.util.formatBytes
import com.wirewhisper.ui.util.rememberDrawableBitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistDetailScreen(
    entryValue: String,
    entryType: String,
    onBack: () -> Unit,
    viewModel: WatchlistDetailViewModel = viewModel(),
) {
    LaunchedEffect(entryValue, entryType) {
        viewModel.init(entryValue, entryType)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val historyFlows by viewModel.historyFlows.collectAsStateWithLifecycle()

    val liveGroups = remember(uiState.liveFlows) { viewModel.groupByApp(uiState.liveFlows) }
    val historyGroups = remember(historyFlows) { viewModel.groupByApp(historyFlows) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(entryValue, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace))
                        Text(entryType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (liveGroups.isNotEmpty()) {
                item {
                    SectionHeader("Live Connections (${uiState.liveFlows.size})")
                }
                items(liveGroups, key = { "live-${it.appName}" }) { group ->
                    AppFlowGroupCard(group)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            item {
                SectionHeader("History (${historyFlows.size} flows)")
            }

            if (historyGroups.isEmpty()) {
                item {
                    Text(
                        "No recorded flows yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(historyGroups, key = { "hist-${it.appName}" }) { group ->
                    AppFlowGroupCard(group)
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun AppFlowGroupCard(group: AppFlowGroup) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val bitmap = rememberDrawableBitmap(group.icon, size = 48)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = group.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatBytes(group.totalBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))

            group.flows.take(10).forEachIndexed { index, flow ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                FlowRow(flow)
            }

            if (group.flows.size > 10) {
                Text(
                    text = "+${group.flows.size - 10} more flows",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FlowRow(flow: FlowRecord) {
    val dateFormat = remember { SimpleDateFormat("MMM d HH:mm", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = flow.protocol.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = ":${flow.dstPort}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatBytes(flow.bytesSent + flow.bytesReceived),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
        )
        if (flow.country != null) {
            Text(
                text = flow.country!!,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = dateFormat.format(Date(flow.lastSeen)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

