package com.wirewhisper.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wirewhisper.core.model.FlowRecord
import com.wirewhisper.core.model.Protocol
import com.wirewhisper.ui.util.FastScrollColumn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    onFlowClick: (Long) -> Unit,
    viewModel: HistoryViewModel = viewModel(),
) {
    val flows by viewModel.flows.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val apps by viewModel.availableApps.collectAsStateWithLifecycle()
    val countries by viewModel.availableCountries.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("History", style = MaterialTheme.typography.headlineSmall)
            Text(
                "${flows.size} flows",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Filter chips
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // App filter
            var showAppMenu by remember { mutableStateOf(false) }
            FilterChip(
                selected = filter.app != null,
                onClick = { showAppMenu = true },
                label = { Text(filter.app ?: "App") },
                trailingIcon = if (filter.app != null) {
                    {
                        Icon(
                            Icons.Default.Close, "Clear",
                            modifier = Modifier.clickable {
                                viewModel.setFilter(filter.copy(app = null))
                            }
                        )
                    }
                } else null,
            )
            DropdownMenu(expanded = showAppMenu, onDismissRequest = { showAppMenu = false }) {
                apps.forEach { app ->
                    DropdownMenuItem(
                        text = { Text(app, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            viewModel.setFilter(filter.copy(app = app))
                            showAppMenu = false
                        },
                    )
                }
            }

            // Country filter
            var showCountryMenu by remember { mutableStateOf(false) }
            FilterChip(
                selected = filter.country != null,
                onClick = { showCountryMenu = true },
                label = { Text(filter.country ?: "Country") },
                trailingIcon = if (filter.country != null) {
                    {
                        Icon(
                            Icons.Default.Close, "Clear",
                            modifier = Modifier.clickable {
                                viewModel.setFilter(filter.copy(country = null))
                            }
                        )
                    }
                } else null,
            )
            DropdownMenu(expanded = showCountryMenu, onDismissRequest = { showCountryMenu = false }) {
                countries.forEach { country ->
                    DropdownMenuItem(
                        text = { Text(country) },
                        onClick = {
                            viewModel.setFilter(filter.copy(country = country))
                            showCountryMenu = false
                        },
                    )
                }
            }

            // Protocol filter
            var showProtoMenu by remember { mutableStateOf(false) }
            FilterChip(
                selected = filter.protocol != null,
                onClick = { showProtoMenu = true },
                label = { Text(filter.protocol?.name ?: "Protocol") },
                trailingIcon = if (filter.protocol != null) {
                    {
                        Icon(
                            Icons.Default.Close, "Clear",
                            modifier = Modifier.clickable {
                                viewModel.setFilter(filter.copy(protocol = null))
                            }
                        )
                    }
                } else null,
            )
            DropdownMenu(expanded = showProtoMenu, onDismissRequest = { showProtoMenu = false }) {
                listOf(Protocol.TCP, Protocol.UDP).forEach { proto ->
                    DropdownMenuItem(
                        text = { Text(proto.name) },
                        onClick = {
                            viewModel.setFilter(filter.copy(protocol = proto))
                            showProtoMenu = false
                        },
                    )
                }
            }

            // Blocked filter
            FilterChip(
                selected = filter.blocked == true,
                onClick = {
                    viewModel.setFilter(
                        filter.copy(blocked = if (filter.blocked == true) null else true)
                    )
                },
                label = { Text("Blocked") },
                leadingIcon = if (filter.blocked == true) {
                    { Icon(Icons.Default.Block, null, modifier = Modifier.size(16.dp)) }
                } else null,
            )

            // Clear all
            if (filter.app != null || filter.country != null || filter.protocol != null || filter.blocked != null) {
                TextButton(onClick = { viewModel.clearFilters() }) {
                    Text("Clear all")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Flow list
        if (flows.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "No recorded flows yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val listState = rememberLazyListState()
            FastScrollColumn(
                listState = listState,
                itemCount = flows.size,
                timestampForIndex = { index -> flows.getOrNull(index)?.lastSeen ?: 0L },
            ) {
                LazyColumn(state = listState) {
                    items(flows) { flow ->
                        HistoryFlowItem(
                            flow = flow,
                            onClick = { onFlowClick(flow.key.hashCode().toLong()) },
                        )
                    }
                }
            }
        }
    }
}

private fun formatBlockReason(reason: String): String = when {
    reason == "app" -> "Blocked app"
    reason == "hostname" -> "Blocked host"
    reason.startsWith("country:") -> "Blocked country (${reason.removePrefix("country:")})"
    else -> reason
}

@Composable
private fun HistoryFlowItem(flow: FlowRecord, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (flow.blocked)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (flow.blocked) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = "Blocked",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = flow.appName ?: flow.packageName ?: "UID ${flow.uid}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = dateFormat.format(Date(flow.lastSeen)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${flow.dnsHostname ?: flow.dstAddress.hostAddress}:${flow.dstPort}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                if (flow.blocked && flow.blockReason != null) {
                    Text(
                        text = formatBlockReason(flow.blockReason!!),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = "${flow.protocol.name} ${flow.country ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
