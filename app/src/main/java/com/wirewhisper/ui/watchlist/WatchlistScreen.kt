package com.wirewhisper.ui.watchlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wirewhisper.watchlist.WatchlistEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    onBack: () -> Unit,
    onEntryClick: (WatchlistEntry) -> Unit,
    viewModel: WatchlistViewModel = viewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Destination Watchlist") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add destination")
            }
        },
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Add a hostname or IP to monitor all traffic to it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
            ) {
                items(entries, key = { it.id }) { entry ->
                    WatchlistEntryItem(
                        entry = entry,
                        onClick = { onEntryClick(entry) },
                        onDelete = { viewModel.removeEntry(entry.id) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddWatchlistDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { value ->
                viewModel.addEntry(value)
                showAddDialog = false
            },
            onPaste = { text ->
                viewModel.addEntries(text)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun WatchlistEntryItem(
    entry: WatchlistEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        onClick = onClick,
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = entry.value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
            },
            supportingContent = {
                Text(
                    text = "${entry.type} \u2022 added ${dateFormat.format(Date(entry.createdAt))}",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            trailingContent = {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )
    }
}

@Composable
private fun AddWatchlistDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onPaste: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Watch Destination") },
        text = {
            Column {
                Text(
                    "Enter a hostname or IP address, or paste a list (one per line).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("e.g. example.com or 8.8.8.8") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 8,
                    trailingIcon = {
                        IconButton(onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                input = clip
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val text = input.trim()
                    if (text.isNotBlank()) {
                        if (text.contains('\n')) {
                            onPaste(text)
                        } else {
                            onAdd(WatchlistViewModel.extractHostname(text))
                        }
                    }
                },
                enabled = input.isNotBlank(),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
