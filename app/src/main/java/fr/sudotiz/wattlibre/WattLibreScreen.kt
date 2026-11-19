package fr.sudotiz.wattlibre

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF07111B)
private val Panel = Color(0xFF101E2B)
private val PanelRaised = Color(0xFF172838)
private val Mint = Color(0xFF76E8BC)
private val Discharge = Color(0xFFFF9E80)
private val White = Color(0xFFF2F7F6)
private val Muted = Color(0xFF9BAEB8)

@Composable
internal fun WattLibreScreen(
    devices: List<BatteryDevice>,
    deviceNames: Map<String, String>,
    scanning: Boolean,
    connected: Boolean,
    status: String,
    selectedDevice: BatteryDevice?,
    selectedDeviceName: String,
    bluetoothEnabled: Boolean?,
    gattReport: GattReport?,
    telemetry: BatteryTelemetry?,
    holdingControlState: HoldingControlState?,
    telemetryStale: Boolean,
    lastUpdatedAt: String?,
    autoRefreshSeconds: Int,
    onScan: () -> Unit,
    onConnect: (BatteryDevice) -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onSetAutoRefreshSeconds: (Int) -> Unit,
    onControl: (OutputControl) -> Unit,
    onCycleLightMode: () -> Unit,
    onRenameDevice: (String) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onReadDcDiagnostics: () -> Unit,
    onCopyDiagnostics: () -> Unit,
) {
    BackHandler(enabled = selectedDevice != null, onBack = onDisconnect)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mint,
            onPrimary = Ink,
            background = Ink,
            surface = Panel,
            onSurface = White,
            secondary = Mint,
        ),
    ) {
        Scaffold(containerColor = Ink) { insets ->
            if (selectedDevice == null) {
                DevicePickerScreen(
                    devices, deviceNames, scanning, status, bluetoothEnabled,
                    onScan, onConnect, onOpenBluetoothSettings,
                    Modifier.fillMaxSize().padding(insets),
                )
            } else {
                DeviceDetailsScreen(
                    device = selectedDevice,
                    deviceName = selectedDeviceName,
                    connected = connected,
                    status = status,
                    bluetoothEnabled = bluetoothEnabled,
                    gattReport = gattReport,
                    telemetry = telemetry,
                    holdingState = holdingControlState,
                    telemetryStale = telemetryStale,
                    lastUpdatedAt = lastUpdatedAt,
                    autoRefreshSeconds = autoRefreshSeconds,
                    onBack = onDisconnect,
                    onRefresh = onRefresh,
                    onSetAutoRefreshSeconds = onSetAutoRefreshSeconds,
                    onDisconnect = onDisconnect,
                    onControl = onControl,
                    onCycleLightMode = onCycleLightMode,
                    onRename = onRenameDevice,
                    onOpenBluetoothSettings = onOpenBluetoothSettings,
                    onRead = onReadDcDiagnostics,
                    onCopy = onCopyDiagnostics,
                    modifier = Modifier.fillMaxSize().padding(insets),
                )
            }
        }
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
        Text(subtitle, color = Muted, fontSize = 15.sp)
    }
}

@Composable
private fun DevicePickerScreen(
    devices: List<BatteryDevice>,
    deviceNames: Map<String, String>,
    scanning: Boolean,
    status: String,
    bluetoothEnabled: Boolean?,
    onScan: () -> Unit,
    onConnect: (BatteryDevice) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { PageTitle(stringResource(R.string.picker_title), stringResource(R.string.picker_subtitle)) }
        if (bluetoothEnabled == false) item { BluetoothOffCard(onOpenBluetoothSettings) }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(status, color = Muted, fontSize = 13.sp)
                    Button(
                        onClick = onScan,
                        enabled = bluetoothEnabled != false,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                    ) { Text(stringResource(if (scanning) R.string.scan_stop else R.string.scan_start), fontWeight = FontWeight.Bold) }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.devices_detected), color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(devices.size.toString().padStart(2, '0'), color = Muted, fontSize = 13.sp)
            }
        }
        if (devices.isEmpty()) item { EmptyDevicesCard(scanning) }
        else items(devices, key = { it.address }) { device ->
            DeviceCard(device, deviceNames[device.address] ?: device.name, onConnect)
        }
    }
}

@Composable
private fun DeviceDetailsScreen(
    device: BatteryDevice,
    deviceName: String,
    connected: Boolean,
    status: String,
    bluetoothEnabled: Boolean?,
    gattReport: GattReport?,
    telemetry: BatteryTelemetry?,
    holdingState: HoldingControlState?,
    telemetryStale: Boolean,
    lastUpdatedAt: String?,
    autoRefreshSeconds: Int,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSetAutoRefreshSeconds: (Int) -> Unit,
    onDisconnect: () -> Unit,
    onControl: (OutputControl) -> Unit,
    onCycleLightMode: () -> Unit,
    onRename: (String) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRead: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingName by remember(device.address, deviceName) { mutableStateOf(false) }
    var draftName by remember(device.address, deviceName) { mutableStateOf(deviceName) }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Button(onClick = onBack, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                Text(stringResource(R.string.back_devices))
            }
        }
        item {
            val subtitle = if (device.name == deviceName) null else stringResource(R.string.device_bluetooth_name, device.name)
            DeviceTitle(deviceName, subtitle) { editingName = true; draftName = deviceName }
        }
        if (bluetoothEnabled == false) item { BluetoothOffCard(onOpenBluetoothSettings) }
        item {
            ConnectionCard(
                connected = connected,
                status = status,
                autoRefreshSeconds = autoRefreshSeconds,
                onDisconnect = onDisconnect,
                onRefresh = onRefresh,
                onSetAutoRefreshSeconds = onSetAutoRefreshSeconds,
            )
        }
        if (telemetry != null) {
            item { TelemetryCard(telemetry, telemetryStale, lastUpdatedAt, autoRefreshSeconds) }
                if (telemetry.expansionBattery1Percent != null || telemetry.expansionBattery2Percent != null) {
                    item {
                        MetricsSection(
                            stringResource(R.string.expansion_batteries),
                            listOfNotNull(
                                telemetry.expansionBattery1Percent?.let { stringResource(R.string.expansion_battery_number, 1) to formatTenths(it, "%") },
                                telemetry.expansionBattery2Percent?.let { stringResource(R.string.expansion_battery_number, 2) to formatTenths(it, "%") },
                        ),
                    )
                }
            }
        }
        if (connected) item { OutputControlsCard(telemetry, holdingState, onControl, onCycleLightMode) }
        item {
            DiagnosticsCard(
                telemetry = telemetry,
                holdingState = holdingState,
                gattReport = gattReport,
                connected = connected,
                telemetryStale = telemetryStale,
                lastUpdatedAt = lastUpdatedAt,
                onRead = onRead,
                onCopy = onCopy,
            )
        }
    }
    if (editingName) {
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text(stringResource(R.string.rename_title)) },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it.take(40) },
                    label = { Text(stringResource(R.string.rename_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(draftName.trim())
                        editingName = false
                    },
                    enabled = draftName.isNotBlank(),
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text(stringResource(R.string.cancel)) } },
            containerColor = Panel,
        )
    }
}

@Composable
private fun DeviceTitle(title: String, subtitle: String?, onRename: () -> Unit) {
    val renameDescription = stringResource(R.string.rename_title)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            TextButton(onClick = onRename, modifier = Modifier.semantics { contentDescription = renameDescription }, contentPadding = PaddingValues(6.dp)) { Text("✏️", fontSize = 19.sp) }
        }
        if (subtitle != null) Text(subtitle, color = Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BluetoothOffCard(onOpenSettings: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF351C20))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.bluetooth_disabled), color = Color(0xFFFFB4AB), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.bluetooth_disabled_body), color = White, fontSize = 13.sp)
            Button(onClick = onOpenSettings, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.open_bluetooth_settings)) }
        }
    }
}

@Composable
private fun ConnectionCard(
    connected: Boolean,
    status: String,
    autoRefreshSeconds: Int,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onSetAutoRefreshSeconds: (Int) -> Unit,
) {
    var intervalText by remember(autoRefreshSeconds) { mutableStateOf(autoRefreshSeconds.toString()) }
    val interval = intervalText.toIntOrNull()?.takeIf { it in 0..MAX_AUTO_REFRESH_SECONDS }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(color = if (connected) Mint.copy(alpha = 0.16f) else PanelRaised, shape = CircleShape) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Spacer(Modifier.size(7.dp).background(if (connected) Mint else Muted, CircleShape))
                        Text(stringResource(if (connected) R.string.status_connected else R.string.status_offline), color = if (connected) Mint else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onDisconnect,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PanelRaised, contentColor = White),
                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp),
                ) { Text(stringResource(if (connected) R.string.disconnect else R.string.choose_another_device), fontWeight = FontWeight.Bold) }
            }

            ConnectionStatus(status, Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { intervalText = it.filter { char -> char in '0'..'9' }.take(4) },
                    modifier = Modifier.width(96.dp),
                    label = { Text(stringResource(R.string.auto_refresh_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(stringResource(R.string.auto_refresh_help), color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(
                    onClick = { interval?.let(onSetAutoRefreshSeconds) },
                    enabled = interval != null && interval != autoRefreshSeconds,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(stringResource(R.string.auto_refresh_apply), fontSize = 13.sp) }
                Button(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PanelRaised, contentColor = Mint),
                ) {
                    Text("🔄 ${stringResource(R.string.refresh)}", fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatus(status: String, modifier: Modifier = Modifier) {
    val emoji = statusEmoji(status)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(emoji, fontSize = 22.sp)
        Text(status, color = if (emoji == "⚠️") Color(0xFFFFB4AB) else Muted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun statusEmoji(message: String): String {
    val status = message.lowercase()
    return when {
        listOf("bluetooth is off", "bluetooth désactivé", "failed", "failure", "échec", "impossible", "denied", "refus", "interrupted", "interrompue", "stale", "périmé", "invalid", "invalide", "no response", "aucune réponse", "timeout", "dépassé", "not confirmed", "unconfirmed", "non confirmé").any(status::contains) -> "⚠️"
        listOf("reading", "read in progress", "lecture", "checking", "vérification", "préparation", "preparing").any(status::contains) -> "📡"
        listOf("discover", "découverte").any(status::contains) -> "🔍"
        listOf("connecting", "connection in progress", "connexion").any(status::contains) -> "🔌"
        listOf("reconnect", "reconnexion").any(status::contains) -> "🔄"
        listOf("updated", "actualis", "received", "reçu", "confirmed", "confirmé", "read without writes", "lu sans écriture").any(status::contains) -> "✅"
        "notification" in status -> "🔔"
        listOf("updates are off", "actualisation automatique désactivée").any(status::contains) -> "💤"
        else -> "ℹ️"
    }
}

@Composable
private fun EmptyDevicesCard(scanning: Boolean) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(if (scanning) R.string.scan_in_progress else R.string.scan_empty_title), color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(if (scanning) R.string.scan_empty_scanning_help else R.string.scan_empty_help),
                color = Muted,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun DeviceCard(device: BatteryDevice, displayName: String, onConnect: (BatteryDevice) -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(color = Mint.copy(alpha = 0.13f), shape = RoundedCornerShape(14.dp)) {
                Text("BLE", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(displayName, color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (displayName != device.name) Text(device.name, color = Muted, fontSize = 11.sp)
                Text("${device.address} · ${device.rssi} dBm", color = Muted, fontSize = 11.sp)
            }
            Button(
                onClick = { onConnect(device) },
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PanelRaised, contentColor = Mint),
            ) {
                Text(stringResource(R.string.choose_device), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TelemetryCard(telemetry: BatteryTelemetry, stale: Boolean, lastUpdatedAt: String?, autoRefreshSeconds: Int) {
    var inputsExpanded by remember { mutableStateOf(false) }
    var outputsExpanded by remember { mutableStateOf(false) }
    val netPower = telemetry.netPowerWatts
    val flowColor = when {
        netPower > 0 -> Mint
        netPower < 0 -> Discharge
        else -> Muted
    }
    val flowLabel = stringResource(
        when {
            netPower > 0 -> R.string.battery_charging
            netPower < 0 -> R.string.battery_discharging
            else -> R.string.battery_balanced
        },
    )
    val remaining = if (telemetry.minutesToEmpty > 0) formatDuration(telemetry.minutesToEmpty) else "—"
    val full = if (telemetry.minutesToFull > 0) formatDuration(telemetry.minutesToFull) else "—"
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.battery_charge), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("${"%.1f".format(java.util.Locale.getDefault(), telemetry.stateOfChargePercent)}%", color = White, fontSize = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                }
                Surface(color = flowColor.copy(alpha = 0.13f), shape = CircleShape) {
                    Text(flowLabel, color = flowColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                }
            }
            Box(
                Modifier.fillMaxWidth().height(8.dp).background(PanelRaised, RoundedCornerShape(8.dp)),
            ) {
                Box(
                    Modifier.fillMaxWidth((telemetry.stateOfChargePercent / 100f).coerceIn(0f, 1f))
                        .height(8.dp).background(Mint, RoundedCornerShape(8.dp)),
                )
            }
            NetPowerCard(netPower, flowColor)
            Text(
                when {
                    lastUpdatedAt == null -> stringResource(R.string.data_waiting)
                    stale -> stringResource(R.string.data_stale, lastUpdatedAt)
                    autoRefreshSeconds == 0 -> stringResource(R.string.auto_refresh_off) + " · " + lastUpdatedAt
                    else -> stringResource(R.string.data_fresh, lastUpdatedAt)
                },
                color = if (stale) Color(0xFFFFB4AB) else Muted,
                fontSize = 12.sp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PowerMetric(stringResource(R.string.metric_total_input), "${telemetry.totalInputWatts} W", Modifier.weight(1f))
                    PowerMetric(stringResource(R.string.metric_total_output), "${telemetry.totalOutputWatts} W", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PowerMetric(stringResource(R.string.metric_autonomy), remaining, Modifier.weight(1f))
                    PowerMetric(stringResource(R.string.metric_full_charge), full, Modifier.weight(1f))
                }
            }
            ExpandableHeader(stringResource(R.string.section_inputs), inputsExpanded) { inputsExpanded = !inputsExpanded }
            if (inputsExpanded) {
                TelemetryCategory(stringResource(R.string.category_ac), listOf(
                    stringResource(R.string.metric_charging_power) to "${telemetry.acChargingWatts} W",
                    stringResource(R.string.metric_voltage) to formatTenths(telemetry.acInputVoltage, "V"),
                    stringResource(R.string.metric_frequency) to formatHundredths(telemetry.acInputFrequency, "Hz"),
                ))
                TelemetryCategory(stringResource(R.string.category_dc), listOf(stringResource(R.string.metric_input_power) to "${telemetry.dcInputWatts} W"))
            }
            ExpandableHeader(stringResource(R.string.section_outputs), outputsExpanded) { outputsExpanded = !outputsExpanded }
            if (outputsExpanded) {
                TelemetryCategory(stringResource(R.string.category_ac), listOf(
                    stringResource(R.string.metric_output_power) to "${telemetry.acOutputWatts} W",
                    stringResource(R.string.metric_voltage) to formatTenths(telemetry.acOutputVoltage, "V"),
                    stringResource(R.string.metric_frequency) to formatTenths(telemetry.acOutputFrequency, "Hz"),
                ))
                TelemetryCategory(stringResource(R.string.category_dc), listOf(
                    stringResource(R.string.metric_xt60) to formatTenths(telemetry.inputRegister8Raw / 10f, "W"),
                    stringResource(R.string.metric_car_socket) to "${telemetry.inputRegister10Raw} W",
                ))
                TelemetryCategory(stringResource(R.string.category_usb), telemetry.usbOutputWatts.mapIndexed { index, watts -> stringResource(R.string.usb_port, index + 1) to formatWatts(watts) })
                TelemetryCategory(stringResource(R.string.category_lighting), listOf(
                    stringResource(R.string.metric_output_power) to formatWatts(telemetry.ledWatts),
                    stringResource(R.string.lighting_mode) to ledModeName(telemetry.ledMode),
                ))
            }
        }
    }
}

@Composable
private fun NetPowerCard(netPower: Int, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.net_power), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(stringResource(R.string.net_power_explanation), color = Muted, fontSize = 12.sp)
            }
            Text(if (netPower > 0) "+$netPower W" else "$netPower W", color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExpandableHeader(title: String, expanded: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(PanelRaised, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text(stringResource(if (expanded) R.string.hide_details else R.string.show_details), color = Mint, fontSize = 12.sp)
    }
}

@Composable
private fun TelemetryCategory(title: String, values: List<Pair<String, String>>) {
    Column(
        Modifier.fillMaxWidth().background(PanelRaised.copy(alpha = 0.55f), RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        values.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(value, color = White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun PowerMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = PanelRaised, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(value, color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun formatDuration(minutes: Int) = if (minutes < 60) {
    stringResource(R.string.duration_minutes, minutes)
} else {
    stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
}

@Composable
private fun OutputControlsCard(
    telemetry: BatteryTelemetry?,
    holdingState: HoldingControlState?,
    onToggle: (OutputControl) -> Unit,
    onCycleLightMode: () -> Unit,
) {
    fun state(control: OutputControl): Boolean? = when (control) {
        OutputControl.USB -> holdingState?.let { it.isEnabled(control) } ?: telemetry?.usbEnabled
        OutputControl.DC -> holdingState?.let { it.isEnabled(control) } ?: telemetry?.dcEnabled
        OutputControl.AC -> holdingState?.let { it.isEnabled(control) } ?: telemetry?.acEnabled
        OutputControl.LIGHT -> holdingState?.let { it.isEnabled(control) } ?: telemetry?.lightEnabled
    }

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(stringResource(R.string.controls_outputs), color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutputButton(OutputControl.USB, state(OutputControl.USB), onToggle, Modifier.weight(1f))
                OutputButton(OutputControl.DC, state(OutputControl.DC), onToggle, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutputButton(OutputControl.AC, state(OutputControl.AC), onToggle, Modifier.weight(1f))
                LightModeButton(holdingState?.lightMode ?: telemetry?.ledMode, onCycleLightMode, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LightModeButton(mode: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (mode != null && mode != 0) Mint.copy(alpha = 0.16f) else PanelRaised,
            contentColor = if (mode != null && mode != 0) Mint else White,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.lighting_mode), fontWeight = FontWeight.Bold)
            Text(if (mode == null) stringResource(R.string.state_unknown) else ledModeName(mode), fontSize = 11.sp)
        }
    }
}

@Composable
private fun DiagnosticsCard(
    telemetry: BatteryTelemetry?,
    holdingState: HoldingControlState?,
    gattReport: GattReport?,
    connected: Boolean,
    telemetryStale: Boolean,
    lastUpdatedAt: String?,
    onRead: () -> Unit,
    onCopy: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val report = gattReport
    val discoveryProblem = report?.let {
        !it.serviceFound || !it.writeCharacteristicFound || !it.notifyCharacteristicFound || !it.notifyDescriptorFound
    } == true

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) {
                    val opening = !expanded
                    expanded = opening
                    if (opening) onRead()
                }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.diagnostic), color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (discoveryProblem) Text(stringResource(R.string.diagnostic_ble_problem), color = Color(0xFFFFB4AB), fontSize = 12.sp)
                }
                Text(stringResource(if (expanded) R.string.hide_details else R.string.show_details), color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            if (!expanded) return@Column
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRead,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PanelRaised, contentColor = Mint),
                    enabled = connected,
                ) { Text(stringResource(R.string.diagnostic_read_registers), fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onCopy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PanelRaised, contentColor = White),
                    enabled = telemetry != null,
                ) { Text(stringResource(R.string.diagnostic_copy), fontWeight = FontWeight.Bold) }
                if (report != null && discoveryProblem) {
                    Text(stringResource(R.string.diagnostic_ble_discovery), color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    ReportRow(stringResource(R.string.diagnostic_battery_service), report.serviceFound)
                    ReportRow(stringResource(R.string.diagnostic_write_characteristic), report.writeCharacteristicFound)
                    ReportRow(stringResource(R.string.diagnostic_notify_characteristic), report.notifyCharacteristicFound)
                    ReportRow(stringResource(R.string.diagnostic_notify_descriptor), report.notifyDescriptorFound)
                    Text(stringResource(R.string.diagnostic_examined, report.serviceCount, report.characteristicCount), color = Muted, fontSize = 12.sp)
                }
                DiagnosticRow(stringResource(R.string.diagnostic_connection), stringResource(if (connected) R.string.diagnostic_connected else R.string.diagnostic_disconnected))
                DiagnosticRow(stringResource(R.string.diagnostic_last_telemetry), lastUpdatedAt ?: stringResource(R.string.diagnostic_never_received))
                DiagnosticRow(stringResource(R.string.diagnostic_freshness), stringResource(if (telemetryStale) R.string.diagnostic_stale else R.string.diagnostic_up_to_date))
                if (telemetry != null) {
                    Text(stringResource(R.string.diagnostic_decoded_measurements), color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    DiagnosticRow(stringResource(R.string.diagnostic_charge), formatTenths(telemetry.stateOfChargePercent, "%"))
                    DiagnosticRow(stringResource(R.string.diagnostic_total_power), "${telemetry.totalInputWatts} / ${telemetry.totalOutputWatts} W")
                    DiagnosticRow(stringResource(R.string.diagnostic_ac_dc_input), "${telemetry.acChargingWatts} / ${telemetry.dcInputWatts} W")
                    DiagnosticRow(stringResource(R.string.diagnostic_output_ports), "${telemetry.acOutputWatts} / ${formatTenths(telemetry.inputRegister8Raw / 10f, "W")} / ${telemetry.inputRegister10Raw} W")
                    DiagnosticRow(stringResource(R.string.lighting_mode), ledModeName(telemetry.ledMode))
                    DiagnosticRow(stringResource(R.string.diagnostic_voltage_frequency_output), "${formatTenths(telemetry.acOutputVoltage, "V")} / ${formatTenths(telemetry.acOutputFrequency, "Hz")}")
                    DiagnosticRow(stringResource(R.string.diagnostic_voltage_frequency_input), "${formatTenths(telemetry.acInputVoltage, "V")} / ${formatHundredths(telemetry.acInputFrequency, "Hz")}")
                    DiagnosticRow(stringResource(R.string.diagnostic_runtime_full), "${telemetry.minutesToEmpty} / ${telemetry.minutesToFull} min")
                    DiagnosticRow(stringResource(R.string.diagnostic_dc_converter), stringResource(if (telemetry.dcConverterActive) R.string.state_active else R.string.state_stopped))
                    Text(stringResource(R.string.diagnostic_dc_state), color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.diagnostic_dc_explanation), color = Muted, fontSize = 12.sp)
                    DiagnosticRow(stringResource(R.string.diagnostic_dc_master), holdingState?.dc?.toString() ?: stringResource(R.string.diagnostic_not_read))
                    DiagnosticRow(stringResource(R.string.diagnostic_hreg5), holdingState?.holding5Raw?.let(::formatHex) ?: stringResource(R.string.diagnostic_not_read))
                    DiagnosticRow(stringResource(R.string.diagnostic_hreg11), holdingState?.holding11Raw?.let(::formatHex) ?: stringResource(R.string.diagnostic_not_read))
                    DiagnosticRow(stringResource(R.string.diagnostic_hregs_30_32), holdingState?.let { "${it.holding30Raw} / ${it.holding31Raw} / ${it.holding32Raw}" } ?: stringResource(R.string.diagnostic_not_read))
                    DiagnosticRow(stringResource(R.string.diagnostic_iregs_41_42), "${formatHex(telemetry.systemStateHigh)} / ${formatHex(telemetry.systemStateLow)}")
                    DiagnosticRow(stringResource(R.string.diagnostic_iregs_8_10), "${telemetry.inputRegister8Raw} / ${telemetry.inputRegister9Raw} / ${telemetry.inputRegister10Raw}")
                    DiagnosticRow(stringResource(R.string.diagnostic_iregs_75_76), "${formatHex(telemetry.inputRegister75Raw)} / ${formatHex(telemetry.inputRegister76Raw)}")
                    DiagnosticRow(stringResource(R.string.diagnostic_ireg78), telemetry.inputRegister78Raw?.toString() ?: stringResource(R.string.diagnostic_not_available))
                    DiagnosticRow(stringResource(R.string.diagnostic_master), stringResource(if (telemetry.dcEnabled) R.string.state_active else R.string.state_stopped))
                    DiagnosticRow(stringResource(R.string.diagnostic_converter), stringResource(if (telemetry.dcConverterActive) R.string.state_active else R.string.state_stopped))
                    RegisterDump(stringResource(R.string.diagnostic_inputs_title, telemetry.inputRegisters.size), "I", telemetry.inputRegisters)
                } else {
                    Text(stringResource(R.string.diagnostic_waiting), color = Muted, fontSize = 12.sp)
                }
                if (holdingState?.holdingRegisters?.isNotEmpty() == true) {
                    RegisterDump(stringResource(R.string.diagnostic_holdings_title, holdingState.holdingRegisters.size), "H", holdingState.holdingRegisters)
                } else {
                    Text(stringResource(R.string.diagnostic_holdings_not_read), color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun RegisterDump(title: String, prefix: String, values: List<Int>) {
    Text(title, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        values.chunked(2).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { columnIndex, value ->
                    val index = rowIndex * 2 + columnIndex
                    Text(
                        "$prefix${index.toString().padStart(2, '0')}  ${formatHex(value)} ($value)",
                        color = Muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OutputButton(
    control: OutputControl,
    enabled: Boolean?,
    onToggle: (OutputControl) -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = { onToggle(control) },
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled == true) Mint.copy(alpha = 0.16f) else PanelRaised,
            contentColor = if (enabled == true) Mint else White,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(control.labelRes), fontWeight = FontWeight.Bold)
            Text(stringResource(if (enabled == null) R.string.state_unknown else if (enabled) R.string.state_active else R.string.state_stopped), fontSize = 11.sp)
        }
    }
}

@Composable
private fun MetricsSection(title: String, rows: List<Pair<String, String>>) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(value, color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun formatWatts(value: Float) = "${"%.1f".format(java.util.Locale.getDefault(), value)} W"
private fun formatTenths(value: Float, unit: String) = "${"%.1f".format(java.util.Locale.getDefault(), value)} $unit"
private fun formatHundredths(value: Float, unit: String) = "${"%.2f".format(java.util.Locale.getDefault(), value)} $unit"
@Composable
private fun ledModeName(mode: Int) = when (mode) {
    0 -> stringResource(R.string.mode_off)
    1 -> stringResource(R.string.mode_on)
    2 -> stringResource(R.string.mode_sos)
    3 -> stringResource(R.string.mode_flash)
    else -> stringResource(R.string.mode_unknown, mode)
}

@Composable
private fun ReportRow(label: String, found: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(stringResource(if (found) R.string.report_found else R.string.report_missing), color = if (found) Mint else Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatHex(value: Int) = "0x${value.toString(16).uppercase().padStart(4, '0')}"
