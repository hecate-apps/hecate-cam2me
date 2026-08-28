package io.macula.cam2me.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.macula.cam2me.data.KNOWN_STATIONS
import io.macula.cam2me.data.KnownStation
import io.macula.cam2me.data.countryFlagEmoji

private const val CUSTOM_HOST_KEY = ""

@Composable
fun SettingsScreen(
    currentHost: String,
    currentPort: Int,
    onBack: () -> Unit,
    onSave: (host: String, port: Int) -> Unit,
) {
    val matchedKnown = KNOWN_STATIONS.find { it.host == currentHost && it.port == currentPort }
    var selectedHost by remember { mutableStateOf(matchedKnown?.host ?: CUSTOM_HOST_KEY) }
    var customHost by remember { mutableStateOf(if (matchedKnown == null) currentHost else "") }
    var customPort by remember { mutableStateOf(if (matchedKnown == null) currentPort.toString() else "4433") }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text("Back") }
            }

            Text(
                "Station",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Column(modifier = Modifier.selectableGroup()) {
                KNOWN_STATIONS.forEach { station ->
                    StationOptionRow(
                        label = "${countryFlagEmoji(station.country)} ${station.city}, ${station.country}",
                        sublabel = station.host,
                        selected = selectedHost == station.host,
                        onSelect = { selectedHost = station.host },
                    )
                }
                StationOptionRow(
                    label = "Custom",
                    sublabel = null,
                    selected = selectedHost == CUSTOM_HOST_KEY,
                    onSelect = { selectedHost = CUSTOM_HOST_KEY },
                )
            }

            if (selectedHost == CUSTOM_HOST_KEY) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    OutlinedTextField(
                        value = customHost,
                        onValueChange = { customHost = it },
                        label = { Text("Host") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = customPort,
                        onValueChange = { customPort = it },
                        label = { Text("Port") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                enabled = selectedHost != CUSTOM_HOST_KEY || (customHost.isNotBlank() && customPort.toIntOrNull() != null),
                onClick = {
                    val (host, port) = resolvedStation(selectedHost, customHost, customPort)
                    onSave(host, port)
                },
            ) {
                Text("Save")
            }
        }
    }
}

private fun resolvedStation(selectedHost: String, customHost: String, customPort: String): Pair<String, Int> {
    if (selectedHost != CUSTOM_HOST_KEY) {
        val known = KNOWN_STATIONS.first { it.host == selectedHost }
        return known.host to known.port
    }
    return customHost.trim() to (customPort.toIntOrNull() ?: 4433)
}

@Composable
private fun StationOptionRow(label: String, sublabel: String?, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (sublabel != null) {
                Text(sublabel, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
