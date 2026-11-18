package org.wattlibre.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity(), BatteryBleClient.Listener {
    private val devices = mutableStateListOf<BatteryDevice>()
    private var scanning by mutableStateOf(false)
    private var connected by mutableStateOf(false)
    private var status by mutableStateOf("Recherchez une batterie à proximité.")
    private var selectedDevice by mutableStateOf<BatteryDevice?>(null)
    private var selectedDeviceName by mutableStateOf("")
    private val deviceNames = mutableStateMapOf<String, String>()
    private var bluetoothEnabled by mutableStateOf<Boolean?>(null)
    private var gattReport by mutableStateOf<GattReport?>(null)
    private var telemetry by mutableStateOf<BatteryTelemetry?>(null)
    private var holdingControlState by mutableStateOf<HoldingControlState?>(null)
    private var telemetryStale by mutableStateOf(true)
    private var lastUpdatedAt by mutableStateOf<String?>(null)
    private lateinit var bleClient: BatteryBleClient
    private val preferences by lazy { getSharedPreferences("devices", MODE_PRIVATE) }
    private var bluetoothReceiverRegistered = false

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> setBluetoothState(true)
                BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> setBluetoothState(false)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) scan() else status = "Autorisation Bluetooth nécessaire pour rechercher une batterie."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleClient = BatteryBleClient(this, this)
        bluetoothEnabled = readBluetoothState()

        setContent {
            WattLibreScreen(
                devices = devices,
                deviceNames = deviceNames,
                scanning = scanning,
                connected = connected,
                status = status,
                selectedDevice = selectedDevice,
                selectedDeviceName = selectedDeviceName,
                bluetoothEnabled = bluetoothEnabled,
                gattReport = gattReport,
                telemetry = telemetry,
                holdingControlState = holdingControlState,
                telemetryStale = telemetryStale,
                lastUpdatedAt = lastUpdatedAt,
                onScan = ::requestScan,
                onConnect = ::connect,
                onDisconnect = ::disconnect,
                onRefresh = bleClient::refresh,
                onControl = bleClient::toggleOutputControl,
                onCycleLightMode = bleClient::cycleLightMode,
                onRenameDevice = ::renameSelectedDevice,
                onOpenBluetoothSettings = ::openBluetoothSettings,
                onReadDcDiagnostics = bleClient::readControlDiagnostics,
                onCopyDiagnostics = ::copyDiagnostics,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(bluetoothStateReceiver, filter)
        }
        bluetoothReceiverRegistered = true
        bluetoothEnabled = readBluetoothState()
        bluetoothEnabled?.let(bleClient::onBluetoothStateChanged)
    }

    override fun onStop() {
        if (bluetoothReceiverRegistered) {
            unregisterReceiver(bluetoothStateReceiver)
            bluetoothReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        bleClient.close()
        super.onDestroy()
    }

    private fun requestScan() {
        if (scanning) {
            bleClient.stopScan()
            return
        }
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray()) else scan()
    }

    private fun scan() {
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) {
            bluetoothEnabled = false
            status = "Bluetooth LE indisponible sur cet appareil."
            return
        }
        bluetoothEnabled = runCatching { adapter.isEnabled }.getOrNull()
        if (bluetoothEnabled != true) {
            status = "Active le Bluetooth dans les réglages Android, puis réessaie."
            return
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R && !isLocationEnabled()) {
            status = "Active la localisation dans Android pour autoriser le scan BLE."
            return
        }

        devices.clear()
        deviceNames.clear()
        selectedDevice = null
        selectedDeviceName = ""
        gattReport = null
        telemetry = null
        holdingControlState = null
        telemetryStale = true
        lastUpdatedAt = null
        bleClient.startScan()
    }

    @Suppress("DEPRECATION")
    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun connect(device: BatteryDevice) {
        selectedDevice = device
        selectedDeviceName = savedName(device)
        gattReport = null
        telemetry = null
        holdingControlState = null
        telemetryStale = true
        lastUpdatedAt = null
        bleClient.connect(device)
    }

    private fun disconnect() {
        bleClient.disconnect()
        selectedDevice = null
        selectedDeviceName = ""
        gattReport = null
        telemetry = null
        holdingControlState = null
        telemetryStale = true
        lastUpdatedAt = null
        status = "Recherchez une batterie à proximité."
    }

    private fun savedName(device: BatteryDevice): String =
        preferences.getString("device_name_${device.address}", null)?.takeIf { it.isNotBlank() } ?: device.name

    private fun renameSelectedDevice(name: String) {
        val device = selectedDevice ?: return
        val cleanName = name.trim().take(40).takeIf(String::isNotBlank) ?: return
        preferences.edit().putString("device_name_${device.address}", cleanName).apply()
        selectedDeviceName = cleanName
        deviceNames[device.address] = cleanName
    }

    private fun openBluetoothSettings() {
        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    private fun readBluetoothState(): Boolean? = runCatching {
        getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled ?: false
    }.getOrNull()

    private fun setBluetoothState(enabled: Boolean) {
        bluetoothEnabled = enabled
        bleClient.onBluetoothStateChanged(enabled)
    }

    private fun copyDiagnostics() {
        val data = telemetry ?: run {
            Toast.makeText(this, "Aucune télémétrie à copier.", Toast.LENGTH_SHORT).show()
            return
        }
        val controls = holdingControlState
        val text = buildString {
            appendLine("WattLibre · ${selectedDeviceName.ifBlank { selectedDevice?.name ?: "appareil inconnu" }}")
            appendLine("address=${selectedDevice?.address ?: "?"}")
            appendLine("status=$status")
            appendLine("updated=$lastUpdatedAt stale=$telemetryStale connected=$connected")
            appendLine("BLE service=${gattReport?.serviceFound ?: "?"} write=${gattReport?.writeCharacteristicFound ?: "?"} notify=${gattReport?.notifyCharacteristicFound ?: "?"} descriptor=${gattReport?.notifyDescriptorFound ?: "?"} services=${gattReport?.serviceCount ?: "?"} characteristics=${gattReport?.characteristicCount ?: "?"}")
            appendLine("HREG5=${controls?.holding5Raw?.let(::hex16) ?: "?"} HREG11=${controls?.holding11Raw?.let(::hex16) ?: "?"}")
            appendLine("HREG24=${controls?.usb ?: "?"} HREG25=${controls?.dc ?: "?"} HREG26=${controls?.ac ?: "?"} HREG27=${controls?.lightMode ?: "?"}")
            appendLine("HREG30=${controls?.holding30Raw ?: "?"} HREG31=${controls?.holding31Raw ?: "?"} HREG32=${controls?.holding32Raw ?: "?"}")
            appendLine("IREG41=${hex16(data.systemStateHigh)} IREG42=${hex16(data.systemStateLow)} IREG75=${hex16(data.inputRegister75Raw)} IREG76=${hex16(data.inputRegister76Raw)}")
            appendLine("IREG78_raw=${data.inputRegister78Raw ?: "?"} IREG8/9/10=${data.inputRegister8Raw}/${data.inputRegister9Raw}/${data.inputRegister10Raw}")
            appendLine("XT60_r8_W=${data.inputRegister8Raw / 10.0} DC_r9_doc_W=${data.inputRegister9Raw / 10.0} cigarette_r10_W=${data.inputRegister10Raw}")
            appendLine("SOC=${data.stateOfChargePercent}% input=${data.totalInputWatts}W output=${data.totalOutputWatts}W")
            appendLine("INPUT REGISTERS")
            data.inputRegisters.forEachIndexed { index, value -> appendLine("IREG$index=${hex16(value)} ($value)") }
            appendLine("HOLDING REGISTERS")
            controls?.holdingRegisters?.forEachIndexed { index, value -> appendLine("HREG$index=${hex16(value)} ($value)") }
        }
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("WattLibre diagnostic", text))
        Toast.makeText(this, "Diagnostic copié dans le presse-papiers.", Toast.LENGTH_SHORT).show()
    }

    private fun hex16(value: Int) = "0x${value.toString(16).uppercase().padStart(4, '0')}"

    override fun onScanStateChanged(scanning: Boolean) = onUi {
        val wasScanning = this.scanning
        this.scanning = scanning
        if (wasScanning && !scanning && !connected) {
            status = if (devices.isEmpty()) "Aucune batterie détectée." else "Scan terminé · ${devices.size} appareil(s) détecté(s)."
        }
    }

    override fun onDeviceFound(device: BatteryDevice) = onUi {
        deviceNames[device.address] = savedName(device)
        val index = devices.indexOfFirst { it.address == device.address }
        if (index < 0) devices.add(device) else devices[index] = device
    }

    override fun onStatusChanged(message: String) = onUi { status = message }

    override fun onConnectionChanged(connected: Boolean) = onUi {
        this.connected = connected
        if (!connected && telemetry != null) telemetryStale = true
    }

    override fun onGattReport(report: GattReport) = onUi { gattReport = report }

    override fun onTelemetry(telemetry: BatteryTelemetry) = onUi {
        this.telemetry = telemetry
        telemetryStale = false
        lastUpdatedAt = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date())
    }

    override fun onTelemetryStale(stale: Boolean) = onUi { telemetryStale = stale }

    override fun onHoldingControlState(state: HoldingControlState) = onUi { holdingControlState = state }

    private inline fun onUi(crossinline action: () -> Unit) {
        runOnUiThread { action() }
    }
}
