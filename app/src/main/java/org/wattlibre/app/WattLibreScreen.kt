package org.wattlibre.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF07111B)
private val Panel = Color(0xFF101E2B)
private val PanelRaised = Color(0xFF172838)
private val Mint = Color(0xFF76E8BC)
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
    onScan: () -> Unit,
    onConnect: (BatteryDevice) -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
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
                    onBack = onDisconnect,
                    onRefresh = onRefresh,
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
        Text("ÉNERGIE · EN LOCAL", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
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
        item { PageTitle("Choisir une batterie", "WattLibre · contrôle local, sans cloud") }
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
                    ) { Text(if (scanning) "Arrêter la recherche" else "Rechercher une batterie", fontWeight = FontWeight.Bold) }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Appareils détectés", color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
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
    onBack: () -> Unit,
    onRefresh: () -> Unit,
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
                Text("‹ Appareils")
            }
        }
        item {
            DeviceTitle(
                deviceName,
                if (device.name == deviceName) "Suivi local de la batterie" else "Nom Bluetooth : ${device.name}",
            ) { editingName = true; draftName = deviceName }
        }
        if (bluetoothEnabled == false) item { BluetoothOffCard(onOpenBluetoothSettings) }
        item {
            ConnectionCard(
                address = device.address,
                connected = connected,
                status = status,
                onDisconnect = onDisconnect,
                onRefresh = onRefresh,
            )
        }
        if (telemetry != null) {
            item { TelemetryCard(telemetry, telemetryStale, lastUpdatedAt) }
            if (telemetry.expansionBattery1Percent != null || telemetry.expansionBattery2Percent != null) {
                item {
                    MetricsSection(
                        "Batteries d’extension",
                        listOfNotNull(
                            telemetry.expansionBattery1Percent?.let { "Extension 1" to formatTenths(it, "%") },
                            telemetry.expansionBattery2Percent?.let { "Extension 2" to formatTenths(it, "%") },
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
            title = { Text("Renommer l’appareil") },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it.take(40) },
                    label = { Text("Nom affiché") },
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
                ) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text("Annuler") } },
            containerColor = Panel,
        )
    }
}

@Composable
private fun DeviceTitle(title: String, subtitle: String, onRename: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ÉNERGIE · EN LOCAL", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            TextButton(onClick = onRename, contentPadding = PaddingValues(6.dp)) { Text("✏️", fontSize = 19.sp) }
        }
        Text(subtitle, color = Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BluetoothOffCard(onOpenSettings: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF351C20))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Bluetooth désactivé", color = Color(0xFFFFB4AB), fontWeight = FontWeight.Bold)
            Text("Active le Bluetooth pour rechercher ou reconnecter la batterie.", color = White, fontSize = 13.sp)
            Button(onClick = onOpenSettings, shape = RoundedCornerShape(14.dp)) { Text("Ouvrir les réglages Bluetooth") }
        }
    }
}

@Composable
private fun ConnectionCard(
    address: String,
    connected: Boolean,
    status: String,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(color = if (connected) Mint.copy(alpha = 0.16f) else PanelRaised, shape = CircleShape) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Spacer(Modifier.size(7.dp).background(if (connected) Mint else Muted, CircleShape))
                        Text(if (connected) "CONNECTÉ" else "HORS LIGNE", color = if (connected) Mint else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(address, color = Muted, fontSize = 12.sp)
                Text(status, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PanelRaised, contentColor = Mint),
                ) { Text("Actualiser", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onDisconnect,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PanelRaised, contentColor = White),
                ) { Text(if (connected) "Déconnecter" else "Changer d’appareil", fontWeight = FontWeight.Bold) }
            }
        }
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
            Text(if (scanning) "Recherche BLE en cours" else "Rien autour, pour l’instant", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (scanning) "Laisse la batterie allumée et à proximité." else "Lance un scan avec la batterie allumée, à proximité.",
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
                Text("Choisir", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TelemetryCard(telemetry: BatteryTelemetry, stale: Boolean, lastUpdatedAt: String?) {
    var inputsExpanded by remember { mutableStateOf(false) }
    var outputsExpanded by remember { mutableStateOf(false) }
    val remaining = telemetry.minutesToEmpty.takeIf { it > 0 }?.let(::formatDuration) ?: "—"
    val full = telemetry.minutesToFull.takeIf { it > 0 }?.let(::formatDuration) ?: "—"
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("CHARGE BATTERIE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("${"%.1f".format(java.util.Locale.getDefault(), telemetry.stateOfChargePercent)}%", color = White, fontSize = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                }
                Text("SOC", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 9.dp))
            }
            Box(
                Modifier.fillMaxWidth().height(8.dp).background(PanelRaised, RoundedCornerShape(8.dp)),
            ) {
                Box(
                    Modifier.fillMaxWidth((telemetry.stateOfChargePercent / 100f).coerceIn(0f, 1f))
                        .height(8.dp).background(Mint, RoundedCornerShape(8.dp)),
                )
            }
            Text(
                when {
                    lastUpdatedAt == null -> "En attente de la première mesure"
                    stale -> "Données périmées · dernière lecture $lastUpdatedAt"
                    else -> "Données à jour · $lastUpdatedAt"
                },
                color = if (stale) Color(0xFFFFB4AB) else Muted,
                fontSize = 12.sp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PowerMetric("ENTRÉE TOTALE", "${telemetry.totalInputWatts} W", Modifier.weight(1f))
                    PowerMetric("SORTIE TOTALE", "${telemetry.totalOutputWatts} W", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PowerMetric("AUTONOMIE ESTIMÉE", remaining, Modifier.weight(1f))
                    PowerMetric("CHARGE COMPLÈTE", full, Modifier.weight(1f))
                }
            }
            ExpandableHeader("Entrées", inputsExpanded) { inputsExpanded = !inputsExpanded }
            if (inputsExpanded) {
                TelemetryCategory("AC", listOf(
                    "Puissance de charge" to "${telemetry.acChargingWatts} W",
                    "Tension" to formatTenths(telemetry.acInputVoltage, "V"),
                    "Fréquence" to formatHundredths(telemetry.acInputFrequency, "Hz"),
                ))
                TelemetryCategory("DC", listOf("Puissance d’entrée" to "${telemetry.dcInputWatts} W"))
            }
            ExpandableHeader("Sorties", outputsExpanded) { outputsExpanded = !outputsExpanded }
            if (outputsExpanded) {
                TelemetryCategory("AC", listOf(
                    "Puissance" to "${telemetry.acOutputWatts} W",
                    "Tension" to formatTenths(telemetry.acOutputVoltage, "V"),
                    "Fréquence" to formatTenths(telemetry.acOutputFrequency, "Hz"),
                ))
                TelemetryCategory("DC", listOf(
                    "XT60" to formatTenths(telemetry.inputRegister8Raw / 10f, "W"),
                    "Prise voiture" to "${telemetry.inputRegister10Raw} W",
                ))
                TelemetryCategory("USB", telemetry.usbOutputWatts.mapIndexed { index, watts -> "Port ${index + 1}" to formatWatts(watts) })
                TelemetryCategory("Éclairage", listOf(
                    "Puissance" to formatWatts(telemetry.ledWatts),
                    "Mode" to ledModeName(telemetry.ledMode),
                ))
            }
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
        Text(if (expanded) "Masquer  ⌃" else "Afficher  ⌄", color = Mint, fontSize = 12.sp)
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

private fun formatDuration(minutes: Int) = if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"

@Composable
private fun OutputControlsCard(
    telemetry: BatteryTelemetry?,
    holdingState: HoldingControlState?,
    onToggle: (OutputControl) -> Unit,
    onCycleLightMode: () -> Unit,
) {
    fun state(control: OutputControl): Boolean? = holdingState?.let { it.isEnabled(control) } ?: when (control) {
        OutputControl.USB -> telemetry?.usbEnabled
        OutputControl.DC -> telemetry?.dcEnabled
        OutputControl.AC -> telemetry?.acEnabled
        OutputControl.LIGHT -> telemetry?.lightEnabled
    }

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Sorties", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
            Text("Mode éclairage", fontWeight = FontWeight.Bold)
            Text(mode?.let(::ledModeName) ?: "État inconnu", fontSize = 11.sp)
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
                Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Diagnostic", color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (discoveryProblem) Text("Problème de découverte BLE", color = Color(0xFFFFB4AB), fontSize = 12.sp)
                }
                Text(if (expanded) "Masquer  ⌃" else "Afficher  ⌄", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            if (!expanded) return@Column
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (report != null && discoveryProblem) {
                    Text("Découverte BLE", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    ReportRow("Service batterie", report.serviceFound)
                    ReportRow("Caractéristique d’écriture", report.writeCharacteristicFound)
                    ReportRow("Caractéristique de notification", report.notifyCharacteristicFound)
                    ReportRow("Descripteur de notification", report.notifyDescriptorFound)
                    Text("${report.serviceCount} service(s) · ${report.characteristicCount} caractéristique(s) examinés", color = Muted, fontSize = 12.sp)
                }
                DiagnosticRow("Connexion", if (connected) "Connecté" else "Interrompue / hors ligne")
                DiagnosticRow("Dernière télémétrie", lastUpdatedAt ?: "Jamais reçue")
                DiagnosticRow("Fraîcheur", if (telemetryStale) "Périmée" else "À jour")
                if (telemetry != null) {
                    Text("Mesures décodées", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    DiagnosticRow("Charge", formatTenths(telemetry.stateOfChargePercent, "%"))
                    DiagnosticRow("Puissance totale entrée / sortie", "${telemetry.totalInputWatts} / ${telemetry.totalOutputWatts} W")
                    DiagnosticRow("Entrée AC / DC", "${telemetry.acChargingWatts} / ${telemetry.dcInputWatts} W")
                    DiagnosticRow("Sortie AC / XT60 / voiture", "${telemetry.acOutputWatts} / ${formatTenths(telemetry.inputRegister8Raw / 10f, "W")} / ${telemetry.inputRegister10Raw} W")
                    DiagnosticRow("Mode éclairage", ledModeName(telemetry.ledMode))
                    DiagnosticRow("Tension / fréquence AC sortie", "${formatTenths(telemetry.acOutputVoltage, "V")} / ${formatTenths(telemetry.acOutputFrequency, "Hz")}")
                    DiagnosticRow("Tension / fréquence AC entrée", "${formatTenths(telemetry.acInputVoltage, "V")} / ${formatHundredths(telemetry.acInputFrequency, "Hz")}")
                    DiagnosticRow("Autonomie / charge complète", "${telemetry.minutesToEmpty} / ${telemetry.minutesToFull} min")
                    DiagnosticRow("État convertisseur DC", if (telemetry.dcConverterActive) "Actif" else "Arrêté")
                    Text("État du DC", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("HREG25 et input 41 bit 10 suivent le maître ; le bit 7 indique le convertisseur, pas l’état individuel XT60.", color = Muted, fontSize = 12.sp)
                    DiagnosticRow("HREG25 · DC maître", holdingState?.dc?.toString() ?: "Non lu")
                    DiagnosticRow("HREG5", holdingState?.holding5Raw?.let(::formatHex) ?: "Non lu")
                    DiagnosticRow("HREG11", holdingState?.holding11Raw?.let(::formatHex) ?: "Non lu")
                    DiagnosticRow("HREG30 / 31 / 32", holdingState?.let { "${it.holding30Raw} / ${it.holding31Raw} / ${it.holding32Raw}" } ?: "Non lu")
                    DiagnosticRow("IREG41 / 42", "${formatHex(telemetry.systemStateHigh)} / ${formatHex(telemetry.systemStateLow)}")
                    DiagnosticRow("IREG8 / 9 / 10", "${telemetry.inputRegister8Raw} / ${telemetry.inputRegister9Raw} / ${telemetry.inputRegister10Raw}")
                    DiagnosticRow("IREG75 / 76", "${formatHex(telemetry.inputRegister75Raw)} / ${formatHex(telemetry.inputRegister76Raw)}")
                    DiagnosticRow("IREG78", telemetry.inputRegister78Raw?.toString() ?: "Non disponible")
                    DiagnosticRow("Maître DC", if (telemetry.dcEnabled) "Actif" else "Arrêté")
                    DiagnosticRow("Convertisseur DC", if (telemetry.dcConverterActive) "Actif" else "Arrêté")
                    RegisterDump("Registres d’entrée · ${telemetry.inputRegisters.size}", "I", telemetry.inputRegisters)
                } else {
                    Text("Les mesures apparaîtront ici après connexion.", color = Muted, fontSize = 12.sp)
                }
                if (holdingState?.holdingRegisters?.isNotEmpty() == true) {
                    RegisterDump("Holding registers · ${holdingState.holdingRegisters.size}", "H", holdingState.holdingRegisters)
                } else {
                    Text("Holding registers : pas encore lus.", color = Muted, fontSize = 12.sp)
                }
                Button(
                    onClick = onRead,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PanelRaised, contentColor = Mint),
                    enabled = connected,
                ) { Text("Lire les registres", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onCopy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PanelRaised, contentColor = White),
                    enabled = telemetry != null,
                ) { Text("Copier le diagnostic", fontWeight = FontWeight.Bold) }
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
            Text(control.label, fontWeight = FontWeight.Bold)
            Text(if (enabled == null) "État inconnu" else if (enabled) "Actif" else "Arrêté", fontSize = 11.sp)
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
private fun ledModeName(mode: Int) = when (mode) {
    0 -> "Éteint"
    1 -> "Allumé"
    2 -> "SOS"
    3 -> "Clignotant"
    else -> "Inconnu ($mode)"
}

@Composable
private fun ReportRow(label: String, found: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(if (found) "Trouvé" else "Absent", color = if (found) Mint else Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
