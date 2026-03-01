package com.wirewhisper.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wirewhisper.core.model.FlowRecord
import com.wirewhisper.ui.util.formatBytes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowDetailScreen(
    flowId: Long,
    onBack: () -> Unit,
    viewModel: FlowDetailViewModel = viewModel(),
) {
    LaunchedEffect(flowId) {
        viewModel.loadFlow(flowId)
    }

    val flow by viewModel.flow.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flow Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        }
    ) { innerPadding ->
        if (flow == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Flow not found or no longer active",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            FlowDetailContent(
                flow = flow!!,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun FlowDetailContent(flow: FlowRecord, modifier: Modifier = Modifier) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()) }

    Column(modifier = modifier.padding(16.dp)) {
        // App identity
        SectionCard("App Identity") {
            DetailRow("App", flow.appName ?: "Unknown")
            DetailRow("Package", flow.packageName ?: "Unknown")
            DetailRow("UID", flow.uid.toString())
        }

        Spacer(Modifier.height(12.dp))

        // Connection info
        SectionCard("Connection") {
            DetailRow("Protocol", flow.protocol.name)
            DetailRow("Source", "${flow.key.srcAddress.hostAddress}:${flow.key.srcPort}")
            DetailRow("Destination", "${flow.key.dstAddress.hostAddress}:${flow.key.dstPort}")
            flow.dnsHostname?.let { DetailRow("Hostname", it) }
            flow.country?.let { DetailRow("Country", it) }
        }

        Spacer(Modifier.height(12.dp))

        // Traffic stats
        SectionCard("Traffic") {
            DetailRow("Bytes sent", formatBytes(flow.bytesSent))
            DetailRow("Bytes received", formatBytes(flow.bytesReceived))
            DetailRow("Packets sent", flow.packetsSent.toString())
            DetailRow("Packets received", flow.packetsReceived.toString())
        }

        Spacer(Modifier.height(12.dp))

        // Timeline
        SectionCard("Timeline") {
            DetailRow("First seen", dateFormat.format(Date(flow.firstSeen)))
            DetailRow("Last seen", dateFormat.format(Date(flow.lastSeen)))
            val durationMs = flow.lastSeen - flow.firstSeen
            DetailRow("Duration", formatDuration(durationMs))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
    }
}


private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}
