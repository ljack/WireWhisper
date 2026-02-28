package com.wirewhisper.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wirewhisper.WireWhisperApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WireWhisperApp

    private val _geoEnabled = MutableStateFlow(false)
    val geoEnabled = _geoEnabled.asStateFlow()

    fun setGeoEnabled(enabled: Boolean) {
        _geoEnabled.value = enabled
        app.geoResolver.onlineLookupEnabled = enabled
    }
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
) {
    val geoEnabled by viewModel.geoEnabled.collectAsStateWithLifecycle()

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
                        "Send IPs to ip-api.com for enriched data (city, ASN, org). " +
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
}
