package com.wirewhisper.ui.settings

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wirewhisper.WireWhisperApp
import com.wirewhisper.ui.util.countryCodeToFlag
import com.wirewhisper.ui.util.countryDisplayName
import com.wirewhisper.watchlist.WatchlistEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

private data class CountryEntry(val code: String, val flag: String, val name: String)

private val allCountries: List<CountryEntry> by lazy {
    Locale.getISOCountries()
        .map { code ->
            CountryEntry(
                code = code,
                flag = countryCodeToFlag(code),
                name = countryDisplayName(code),
            )
        }
        .sortedBy { it.name }
}

private val countryByCode: Map<String, CountryEntry> by lazy {
    allCountries.associateBy { it.code }
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WireWhisperApp

    private val _geoEnabled = MutableStateFlow(app.geoResolver.onlineLookupEnabled)
    val geoEnabled = _geoEnabled.asStateFlow()

    val blockedCountries: StateFlow<Set<String>> = app.blockingEngine.blockedCountriesFlow
    val watchlistEntries: StateFlow<List<WatchlistEntry>> = app.watchlistEngine.entries

    fun setGeoEnabled(enabled: Boolean) {
        _geoEnabled.value = enabled
        app.geoResolver.onlineLookupEnabled = enabled
    }

    fun toggleCountryBlock(countryCode: String) {
        app.blockingEngine.toggleCountryBlock(countryCode)
    }

    private val _resetInProgress = MutableStateFlow(false)
    val resetInProgress = _resetInProgress.asStateFlow()

    fun resetHistory() {
        viewModelScope.launch {
            _resetInProgress.value = true
            app.resetHistory()
            _resetInProgress.value = false
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigateToWatchlist: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    val geoEnabled by viewModel.geoEnabled.collectAsStateWithLifecycle()
    val blockedCountries by viewModel.blockedCountries.collectAsStateWithLifecycle()
    val watchlistEntries by viewModel.watchlistEntries.collectAsStateWithLifecycle()
    val resetInProgress by viewModel.resetInProgress.collectAsStateWithLifecycle()
    var showCountryPicker by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            ListItem(
                headlineContent = { Text("GeoIP online lookup") },
                supportingContent = {
                    Text(
                        "Send IPs to ipwho.is for enriched data (city, ASN, org). " +
                        "Country-level resolution works offline using the built-in " +
                        "DB-IP database. Toggle this for extra detail only.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = geoEnabled,
                        onCheckedChange = { viewModel.setGeoEnabled(it) },
                    )
                },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ListItem(
                headlineContent = { Text("About") },
                supportingContent = {
                    Text(
                        "WireWhisper v0.1.0\n" +
                        "Privacy-focused network monitor for stock Android.\n" +
                        "All processing is performed on-device. No telemetry.\n\n" +
                        "IP geolocation by DB-IP (db-ip.com), CC BY 4.0.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        // How to Use section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            ListItem(
                headlineContent = { Text("How to Use") },
                supportingContent = {
                    Text(
                        "\u2022 Start monitoring: Tap the play button, accept VPN permission\n" +
                        "\u2022 View traffic: Switch between App/Country views, tap rows to expand, tap sparklines for charts\n" +
                        "\u2022 Block traffic: Tap the shield icon next to apps, hostnames, or countries\n" +
                        "\u2022 History: Browse and filter past connections in the History tab\n" +
                        "\u2022 Privacy: All processing is on-device. Online GeoIP is optional and off by default.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        // Destination Watchlist section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable { onNavigateToWatchlist() },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            ListItem(
                headlineContent = { Text("Destination Watchlist") },
                supportingContent = {
                    Text(
                        if (watchlistEntries.isEmpty()) "Monitor traffic to specific hostnames or IPs"
                        else "${watchlistEntries.size} ${if (watchlistEntries.size == 1) "destination" else "destinations"} watched",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        // Blocked Countries section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable { showCountryPicker = true },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            ListItem(
                headlineContent = { Text("Blocked Countries") },
                supportingContent = {
                    if (blockedCountries.isEmpty()) {
                        Text(
                            "No countries blocked. Tap to add countries whose traffic will be dropped.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text(
                            "${blockedCountries.size} ${if (blockedCountries.size == 1) "country" else "countries"} blocked",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
            )

            if (blockedCountries.isNotEmpty()) {
                val blockedEntries = remember(blockedCountries) {
                    blockedCountries.mapNotNull { countryByCode[it] }.sortedBy { it.name }
                }
                FlowRow(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                ) {
                    blockedEntries.forEach { entry ->
                        InputChip(
                            selected = true,
                            onClick = { viewModel.toggleCountryBlock(entry.code) },
                            label = { Text("${entry.flag} ${entry.name}") },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Unblock",
                                    modifier = Modifier.height(16.dp),
                                )
                            },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Reset History section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable(enabled = !resetInProgress) { showResetConfirm = true },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            ListItem(
                headlineContent = { Text("Reset History") },
                supportingContent = {
                    Text(
                        "Clear all recorded flow history and traffic samples. Blocking rules are preserved.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingContent = {
                    if (resetInProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        // Monitoring mode section – Phase 2
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            ListItem(
                headlineContent = { Text("Monitoring mode") },
                supportingContent = {
                    Text(
                        "Continuous mode (always-on) and Debug Session mode " +
                        "(time-limited capture) will be configurable here in a future release.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }

        Spacer(Modifier.height(80.dp)) // room for bottom nav
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset History") },
            text = {
                Text("This will permanently delete all recorded flow history and traffic samples. Blocking rules will be preserved.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        viewModel.resetHistory()
                    },
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showCountryPicker) {
        BlockedCountriesDialog(
            blockedCountries = blockedCountries,
            onToggle = { viewModel.toggleCountryBlock(it) },
            onDismiss = { showCountryPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockedCountriesDialog(
    blockedCountries: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(search) {
        if (search.isBlank()) allCountries
        else allCountries.filter { it.name.contains(search, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Block Countries",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search countries\u2026") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(filtered, key = { it.code }) { entry ->
                    ListItem(
                        headlineContent = { Text("${entry.flag} ${entry.name}") },
                        trailingContent = {
                            Checkbox(
                                checked = entry.code in blockedCountries,
                                onCheckedChange = { onToggle(entry.code) },
                            )
                        },
                        modifier = Modifier.clickable { onToggle(entry.code) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
