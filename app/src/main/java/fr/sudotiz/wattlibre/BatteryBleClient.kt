package fr.sudotiz.wattlibre

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattConnectionSettings
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.util.UUID

data class BatteryDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int,
)

data class GattReport(
    val serviceFound: Boolean,
    val writeCharacteristicFound: Boolean,
    val notifyCharacteristicFound: Boolean,
    val notifyDescriptorFound: Boolean,
    val serviceCount: Int,
    val characteristicCount: Int,
)

internal const val MAX_AUTO_REFRESH_SECONDS = 3_600

internal class BatteryBleClient(
    context: Context,
    private val listener: Listener,
) {
    private data class PendingControl(
        val control: OutputControl,
        val cycleLight: Boolean = false,
        val expectedValue: Int? = null,
    )
    private enum class DiagnosticPhase { NONE, INPUT, HOLDING }

    interface Listener {
        fun onScanStateChanged(scanning: Boolean)
        fun onDeviceFound(device: BatteryDevice)
        fun onStatusChanged(message: String)
        fun onConnectionChanged(connected: Boolean)
        fun onGattReport(report: GattReport)
        fun onTelemetry(telemetry: BatteryTelemetry)
        fun onTelemetryStale(stale: Boolean)
        fun onHoldingControlState(state: HoldingControlState)
    }

    private val context = context.applicationContext
    private val bluetoothManager = this.context.getSystemService(BluetoothManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val foundDevices = mutableMapOf<String, BatteryDevice>()
    private val responseBuffer = ByteArrayOutputStream()
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notificationsReady = false
    private var readPending = false
    private var pendingHoldingRead = false
    private var queuedControl: PendingControl? = null
    private var controlAwaitingConfirmation: PendingControl? = null
    private var diagnosticPhase = DiagnosticPhase.NONE
    private var scanTimeout: Runnable? = null
    private var connectionTimeout: Runnable? = null
    private var readTimeout: Runnable? = null
    private var pollTask: Runnable? = null
    private var pollTaskAutomatic = false
    private var controlTimeout: Runnable? = null
    private var expectedDevice: BatteryDevice? = null
    private var reconnectTask: Runnable? = null
    private var reconnectDelayMs = 1_000L
    private var connectionLost = false
    private var telemetryStale = true
    private var pollIntervalMs = DEFAULT_POLL_INTERVAL_MS

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName?.takeIf(String::isNotBlank)
                ?: runCatching { result.device.name?.takeIf(String::isNotBlank) }.getOrNull()
                ?: message(R.string.device_unknown_name)
            val device = BatteryDevice(result.device, name, result.device.address, result.rssi)
            val previous = foundDevices.put(device.address, device)
            if (previous == null || previous.name != device.name || previous.rssi != device.rssi) listener.onDeviceFound(device)
        }

        override fun onScanFailed(errorCode: Int) {
            stopScan()
            listener.onStatusChanged(message(R.string.ble_scan_failed, errorCode))
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (this@BatteryBleClient.gatt !== gatt) return
            when {
                status != BluetoothGatt.GATT_SUCCESS -> {
                    loseConnection(gatt, message(R.string.ble_interrupted_code, status))
                }
                newState == BluetoothProfile.STATE_CONNECTED -> {
                    connectionLost = false
                    listener.onConnectionChanged(true)
                    listener.onStatusChanged(message(R.string.ble_connected_discovering))
                    if (!gatt.discoverServices()) loseConnection(gatt, message(R.string.ble_services_failed))
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    loseConnection(gatt, message(R.string.ble_interrupted))
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (this@BatteryBleClient.gatt !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onGattReport(GattReport(false, false, false, false, gatt.services.size, 0))
                loseConnection(gatt, message(R.string.ble_discovery_failed, status))
                return
            }

            val service = gatt.getService(SERVICE_UUID)
            val characteristics = service?.characteristics.orEmpty()
            val write = service?.getCharacteristic(WRITE_UUID)
            val notify = service?.getCharacteristic(NOTIFY_UUID)
            val cccd = notify?.getDescriptor(CCCD_UUID)
            listener.onGattReport(
                GattReport(
                    serviceFound = service != null,
                    writeCharacteristicFound = write != null,
                    notifyCharacteristicFound = notify != null,
                    notifyDescriptorFound = cccd != null,
                    serviceCount = gatt.services.size,
                    characteristicCount = characteristics.size,
                ),
            )
            if (write == null || notify == null || cccd == null) {
                loseConnection(gatt, message(R.string.ble_characteristics_incomplete))
                return
            }
            if (!gatt.setCharacteristicNotification(notify, true)) {
                loseConnection(gatt, message(R.string.ble_notifications_failed))
                return
            }

            val cccdValue = when {
                (notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                (notify.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                else -> {
                    loseConnection(gatt, message(R.string.ble_notifications_unsupported))
                    return
                }
            }
            writeCharacteristic = write
            if (!writeDescriptor(gatt, cccd, cccdValue)) {
                loseConnection(gatt, message(R.string.ble_notifications_failed))
            } else {
                listener.onStatusChanged(message(R.string.ble_notifications_enabling))
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (this@BatteryBleClient.gatt !== gatt || descriptor.uuid != CCCD_UUID) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                loseConnection(gatt, message(R.string.ble_descriptor_failed, status))
                return
            }
            reconnectDelayMs = 1_000L
            notificationsReady = true
            listener.onStatusChanged(message(R.string.ble_notifications_ready))
            if (!gatt.requestMtu(247)) sendReadRequest(gatt)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (this@BatteryBleClient.gatt !== gatt || !notificationsReady) return
            listener.onStatusChanged(message(R.string.ble_mtu, mtu))
            sendReadRequest(gatt)
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            receiveNotification(gatt, characteristic, characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            receiveNotification(gatt, characteristic, value)
        }
    }

    fun startScan() {
        stopScan()
        val bluetoothAdapter = bluetoothManager.adapter
        val activeScanner = bluetoothAdapter?.bluetoothLeScanner
        if (activeScanner == null) {
            listener.onStatusChanged(message(R.string.status_bluetooth_unavailable))
            return
        }

        foundDevices.clear()
        scanner = activeScanner
        try {
            activeScanner.startScan(
                null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                scanCallback,
            )
            listener.onScanStateChanged(true)
            listener.onStatusChanged(message(R.string.ble_scan_search))
            scanTimeout = Runnable { stopScan() }.also { mainHandler.postDelayed(it, SCAN_DURATION_MS) }
        } catch (_: SecurityException) {
            scanner = null
            listener.onScanStateChanged(false)
            listener.onStatusChanged(message(R.string.ble_permission_denied))
        }
    }

    fun stopScan() {
        scanTimeout?.let(mainHandler::removeCallbacks)
        scanTimeout = null
        val activeScanner = scanner ?: return
        scanner = null
        runCatching { activeScanner.stopScan(scanCallback) }
        listener.onScanStateChanged(false)
    }

    fun connect(device: BatteryDevice) {
        stopScan()
        disconnect()
        expectedDevice = device
        reconnectDelayMs = 1_000L
        telemetryStale = true
        openConnection(device)
    }

    private fun openConnection(device: BatteryDevice) {
        clearReadState()
        writeCharacteristic = null
        notificationsReady = false
        listener.onConnectionChanged(false)
        listener.onStatusChanged(message(R.string.ble_connecting, device.name))
        try {
            gatt = openGatt(device.device)
            if (gatt == null) {
                listener.onStatusChanged(message(R.string.ble_open_failed))
                scheduleReconnect()
            } else {
                connectionTimeout = Runnable {
                    gatt?.takeIf { expectedDevice?.address == device.address }?.let {
                        loseConnection(it, message(R.string.ble_connect_timeout))
                    }
                }.also { mainHandler.postDelayed(it, CONNECTION_TIMEOUT_MS) }
            }
        } catch (_: SecurityException) {
            listener.onStatusChanged(message(R.string.ble_permission_denied))
        }
    }

    fun disconnect() {
        stopScan()
        expectedDevice = null
        connectionLost = false
        connectionTimeout?.let(mainHandler::removeCallbacks)
        connectionTimeout = null
        reconnectTask?.let(mainHandler::removeCallbacks)
        reconnectTask = null
        clearReadState()
        writeCharacteristic = null
        notificationsReady = false
        gatt?.let { connection ->
            runCatching { connection.disconnect() }
            closeGatt(connection)
        }
        listener.onConnectionChanged(false)
    }

    fun onBluetoothStateChanged(enabled: Boolean) {
        if (!enabled) {
            stopScan()
            reconnectTask?.let(mainHandler::removeCallbacks)
            reconnectTask = null
            gatt?.let { connection ->
                runCatching { connection.disconnect() }
                closeGatt(connection)
            }
            connectionLost = true
            telemetryStale = true
            listener.onConnectionChanged(false)
            listener.onTelemetryStale(true)
            listener.onStatusChanged(message(R.string.ble_bluetooth_off_reconnect))
        } else if (expectedDevice != null && gatt == null) {
            scheduleReconnect(delayMs = 0L)
        }
    }

    fun refresh() {
        val connection = gatt
        if (connection == null || connectionLost) {
            expectedDevice?.let {
                reconnectTask?.let(mainHandler::removeCallbacks)
                reconnectTask = null
                if (isBluetoothEnabled()) openConnection(it)
                else listener.onStatusChanged(message(R.string.ble_bluetooth_off_reconnect))
            } ?: listener.onStatusChanged(message(R.string.ble_no_device_reconnect))
            return
        }
        if (!notificationsReady) {
            listener.onStatusChanged(message(R.string.ble_connection_in_progress))
            return
        }
        if (readPending) {
            listener.onStatusChanged(message(R.string.ble_read_in_progress))
            return
        }
        if (diagnosticPhase != DiagnosticPhase.NONE || queuedControl != null || controlAwaitingConfirmation != null || pollTask?.let { !pollTaskAutomatic } == true) {
            listener.onStatusChanged(message(R.string.ble_wait_current))
            return
        }
        if (telemetryStale) {
            loseConnection(connection, message(R.string.ble_data_stale))
            return
        }
        cancelAutomaticPoll()
        sendReadRequest(connection)
    }

    fun setPollIntervalSeconds(seconds: Int) {
        pollIntervalMs = seconds.coerceIn(0, MAX_AUTO_REFRESH_SECONDS) * 1_000L
        cancelAutomaticPoll()
        if (readPending || diagnosticPhase != DiagnosticPhase.NONE || queuedControl != null || controlAwaitingConfirmation != null || pollTask != null) return
        gatt?.takeIf { notificationsReady && !readPending }?.let(::schedulePoll)
    }

    fun readControlDiagnostics() {
        val connection = gatt
        if (connection == null || !notificationsReady) {
            listener.onStatusChanged(message(R.string.ble_not_ready))
            return
        }
        if (readPending || diagnosticPhase != DiagnosticPhase.NONE || queuedControl != null || controlAwaitingConfirmation != null || pollTask?.let { !pollTaskAutomatic } == true) {
            listener.onStatusChanged(message(R.string.ble_wait_current))
            return
        }
        cancelAutomaticPoll()
        diagnosticPhase = DiagnosticPhase.INPUT
        if (!sendReadRequest(connection)) {
            diagnosticPhase = DiagnosticPhase.NONE
            listener.onStatusChanged(message(R.string.ble_diagnostic_read_failed))
            schedulePoll(connection)
        }
    }

    fun toggleOutputControl(control: OutputControl) {
        queueOutputControl(PendingControl(control))
    }

    fun cycleLightMode() {
        queueOutputControl(PendingControl(OutputControl.LIGHT, cycleLight = true))
    }

    private fun queueOutputControl(request: PendingControl) {
        val connection = gatt
        if (connection == null || !notificationsReady) {
            listener.onStatusChanged(message(R.string.ble_not_ready))
            return
        }
        if (queuedControl != null || controlAwaitingConfirmation != null) {
            listener.onStatusChanged(message(R.string.ble_command_in_progress))
            return
        }
        if (diagnosticPhase != DiagnosticPhase.NONE) {
            listener.onStatusChanged(message(R.string.ble_wait_current))
            return
        }
        if (readPending) {
            queuedControl = request
            listener.onStatusChanged(message(R.string.ble_verify_before_command))
            return
        }

        queuedControl = request
        pollTask?.let(mainHandler::removeCallbacks)
        pollTask = null
        pollTaskAutomatic = false
        if (!sendReadRequest(connection, holdingRegisters = true)) {
            queuedControl = null
            listener.onStatusChanged(message(R.string.ble_verify_failed))
        }
    }

    fun close() = disconnect()

    private fun closeGatt(connection: BluetoothGatt) {
        if (gatt === connection) {
            connectionTimeout?.let(mainHandler::removeCallbacks)
            connectionTimeout = null
            gatt = null
            clearReadState()
            writeCharacteristic = null
            notificationsReady = false
        }
        runCatching { connection.close() }
    }

    private fun loseConnection(connection: BluetoothGatt, reason: String) {
        if (gatt !== connection) return
        runCatching { connection.disconnect() }
        closeGatt(connection)
        connectionLost = true
        telemetryStale = true
        listener.onConnectionChanged(false)
        listener.onTelemetryStale(true)
        listener.onStatusChanged(message(R.string.ble_reason_reconnect, reason, message(R.string.ble_auto_reconnect)))
        scheduleReconnect()
    }

    private fun scheduleReconnect(delayMs: Long? = null) {
        val device = expectedDevice ?: return
        if (!isBluetoothEnabled()) {
            listener.onStatusChanged(message(R.string.ble_bluetooth_off_reconnect))
            return
        }
        reconnectTask?.let(mainHandler::removeCallbacks)
        val delay = delayMs ?: reconnectDelayMs.also {
            reconnectDelayMs = (it * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
        }
        reconnectTask = Runnable {
            reconnectTask = null
            if (expectedDevice?.address == device.address && gatt == null && isBluetoothEnabled()) openConnection(device)
        }.also { mainHandler.postDelayed(it, delay) }
    }

    private fun isBluetoothEnabled() = runCatching { bluetoothManager.adapter?.isEnabled == true }.getOrDefault(false)

    private fun sendReadRequest(connection: BluetoothGatt, holdingRegisters: Boolean = false): Boolean {
        if (gatt !== connection || !notificationsReady || readPending) return false
        val characteristic = writeCharacteristic ?: return false
        val request = if (holdingRegisters) BatteryProtocol.readHoldingRegisters() else BatteryProtocol.readInputRegisters()
        if (!writeGattPayload(connection, characteristic, request)) {
            loseConnection(connection, message(R.string.ble_read_send_failed))
            return false
        }

        readPending = true
        pendingHoldingRead = holdingRegisters
        responseBuffer.reset()
        listener.onStatusChanged(message(R.string.ble_read_in_progress))
        readTimeout = Runnable {
            if (gatt === connection && readPending) {
                readPending = false
                pendingHoldingRead = false
                responseBuffer.reset()
                val controlNotSent = queuedControl != null
                val commandNotConfirmed = controlAwaitingConfirmation != null
                queuedControl = null
                controlAwaitingConfirmation = null
                diagnosticPhase = DiagnosticPhase.NONE
                controlTimeout?.let(mainHandler::removeCallbacks)
                controlTimeout = null
                val statusMessage = when {
                    controlNotSent -> message(R.string.ble_state_unverified)
                    commandNotConfirmed -> message(R.string.ble_command_no_confirmation)
                    else -> message(R.string.ble_no_response)
                }
                loseConnection(connection, statusMessage)
            }
        }.also { mainHandler.postDelayed(it, READ_TIMEOUT_MS) }
        return true
    }

    private fun receiveNotification(
        connection: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        if (gatt !== connection || characteristic.uuid != NOTIFY_UUID || !notificationsReady) return
        responseBuffer.write(value, 0, value.size)
        val bytes = responseBuffer.toByteArray()
        if (bytes.size > MAX_RESPONSE_SIZE) {
            responseBuffer.reset()
            listener.onStatusChanged(message(R.string.ble_response_too_long))
            return
        }
        val holdingResponse = pendingHoldingRead
        val expectedFunction = if (holdingResponse) READ_HOLDING_REGISTERS else READ_INPUT_REGISTERS
        if (bytes.size >= 2 && ((bytes[0].toInt() and 0xFF) != ADDRESS || (bytes[1].toInt() and 0xFF) != expectedFunction)) {
            responseBuffer.reset()
            return
        }
        if (bytes.size < 6) return

        val count = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
        if (count !in (if (holdingResponse) 33 else 79)..REGISTER_COUNT) {
            responseBuffer.reset()
            listener.onStatusChanged(message(R.string.ble_register_range_unexpected))
            return
        }
        val frameSize = 8 + count * 2
        if (bytes.size < frameSize) return

        val frame = bytes.copyOf(frameSize)
        responseBuffer.reset()
        readPending = false
        pendingHoldingRead = false
        readTimeout?.let(mainHandler::removeCallbacks)
        readTimeout = null
        if (holdingResponse) {
            val state = BatteryProtocol.parseHoldingControlState(frame)
            val diagnosticRead = diagnosticPhase == DiagnosticPhase.HOLDING
            diagnosticPhase = DiagnosticPhase.NONE
            if (state == null) {
                listener.onStatusChanged(message(R.string.ble_holding_invalid))
                queuedControl = null
                schedulePoll(connection)
                return
            }
            listener.onHoldingControlState(state)

            val request = queuedControl
            if (request != null) {
                queuedControl = null
                val raw = state.rawValue(request.control)
                val validValue = if (request.control == OutputControl.LIGHT) raw in 0..3 else raw in 0..1
                if (!validValue) {
                    listener.onStatusChanged(message(R.string.ble_hreg_unexpected, request.control.register, raw))
                    schedulePoll(connection)
                    return
                }
                val target = if (request.cycleLight) (raw + 1) % 4 else if (raw == 0) 1 else 0
                val characteristic = writeCharacteristic
                if (characteristic == null || !writeGattPayload(
                        connection,
                        characteristic,
                        if (request.cycleLight) BatteryProtocol.writeLightMode(target)
                        else BatteryProtocol.writeOutputControl(request.control, target != 0),
                    )
                ) {
                    listener.onStatusChanged(message(R.string.ble_write_failed))
                    schedulePoll(connection)
                    return
                }
                val sent = request.copy(cycleLight = false, expectedValue = target)
                controlAwaitingConfirmation = sent
                val controlLabel = context.getString(request.control.labelRes)
                listener.onStatusChanged(message(R.string.ble_command_sent, controlLabel))
                controlTimeout = Runnable {
                    if (controlAwaitingConfirmation == sent) {
                        controlAwaitingConfirmation = null
                        listener.onStatusChanged(message(R.string.ble_command_not_confirmed))
                    }
                }.also { mainHandler.postDelayed(it, CONTROL_CONFIRM_TIMEOUT_MS) }
                scheduleRead(connection, CONTROL_CONFIRM_DELAY_MS, holdingRegisters = true)
                return
            }

            controlAwaitingConfirmation?.let { sent ->
                controlAwaitingConfirmation = null
                controlTimeout?.let(mainHandler::removeCallbacks)
                controlTimeout = null
                val controlLabel = context.getString(sent.control.labelRes)
                listener.onStatusChanged(
                    if (state.rawValue(sent.control) == sent.expectedValue) message(R.string.ble_command_confirmed, controlLabel)
                    else message(R.string.ble_command_unconfirmed, controlLabel),
                )
            } ?: listener.onStatusChanged(message(if (diagnosticRead) R.string.ble_diagnostic_read_done else R.string.ble_measures_received))
            schedulePoll(connection)
            return
        }
        val telemetry = BatteryProtocol.parseInputResponse(frame)
        if (telemetry == null) {
            val controlNotSent = queuedControl != null
            queuedControl = null
            diagnosticPhase = DiagnosticPhase.NONE
            telemetryStale = true
            listener.onTelemetryStale(true)
            listener.onStatusChanged(if (controlNotSent) message(R.string.ble_state_unverified) else message(R.string.ble_bad_response))
            schedulePoll(connection)
            return
        }

        telemetryStale = false
        connectionTimeout?.let(mainHandler::removeCallbacks)
        connectionTimeout = null
        listener.onTelemetry(telemetry)
        listener.onTelemetryStale(false)
        if (diagnosticPhase == DiagnosticPhase.INPUT) {
            diagnosticPhase = DiagnosticPhase.HOLDING
            listener.onStatusChanged(message(R.string.ble_input_updated))
            scheduleRead(connection, DIAGNOSTIC_DELAY_MS, holdingRegisters = true)
            return
        }
        if (queuedControl != null) {
            listener.onStatusChanged(message(R.string.ble_read_controls_before_action))
            scheduleRead(connection, CONTROL_VERIFY_DELAY_MS, holdingRegisters = true)
            return
        }
        if (controlAwaitingConfirmation != null) {
            listener.onStatusChanged(message(R.string.ble_wait_confirmation))
            scheduleRead(connection, CONTROL_CONFIRM_DELAY_MS, holdingRegisters = true)
            return
        }
        listener.onStatusChanged(message(R.string.ble_input_updated))
        scheduleRead(connection, CONTROL_VERIFY_DELAY_MS, holdingRegisters = true)
    }

    private fun writeGattPayload(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray): Boolean {
        val properties = characteristic.properties
        if ((properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0) {
            listener.onStatusChanged(message(R.string.ble_characteristic_no_write))
            return false
        }
        val type = if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        return writeGattCharacteristic(connection, characteristic, value, type)
    }

    private fun scheduleRead(connection: BluetoothGatt, delayMs: Long, holdingRegisters: Boolean = false, automaticPoll: Boolean = false) {
        pollTask?.let(mainHandler::removeCallbacks)
        pollTaskAutomatic = automaticPoll
        pollTask = Runnable {
            pollTask = null
            pollTaskAutomatic = false
            sendReadRequest(connection, holdingRegisters)
        }.also { mainHandler.postDelayed(it, delayMs) }
    }

    private fun schedulePoll(connection: BluetoothGatt) {
        if (pollIntervalMs == 0L) return
        scheduleRead(connection, pollIntervalMs, automaticPoll = true)
    }

    private fun cancelAutomaticPoll() {
        if (!pollTaskAutomatic) return
        pollTask?.let(mainHandler::removeCallbacks)
        pollTask = null
        pollTaskAutomatic = false
    }

    private fun writeDescriptor(
        connection: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
    ): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        connection.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
    } else {
        writeDescriptorLegacy(connection, descriptor, value)
    }

    @Suppress("DEPRECATION")
    private fun writeDescriptorLegacy(
        connection: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
    ): Boolean {
        descriptor.value = value
        return connection.writeDescriptor(descriptor)
    }

    private fun writeGattCharacteristic(
        connection: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
    ): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        connection.writeCharacteristic(characteristic, value, writeType) == BluetoothStatusCodes.SUCCESS
    } else {
        writeCharacteristicLegacy(connection, characteristic, value, writeType)
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristicLegacy(
        connection: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
    ): Boolean {
        characteristic.writeType = writeType
        characteristic.value = value
        return connection.writeCharacteristic(characteristic)
    }

    private fun clearReadState() {
        readTimeout?.let(mainHandler::removeCallbacks)
        pollTask?.let(mainHandler::removeCallbacks)
        readTimeout = null
        pollTask = null
        pollTaskAutomatic = false
        controlTimeout?.let(mainHandler::removeCallbacks)
        controlTimeout = null
        readPending = false
        pendingHoldingRead = false
        queuedControl = null
        controlAwaitingConfirmation = null
        diagnosticPhase = DiagnosticPhase.NONE
        responseBuffer.reset()
    }

    @Suppress("DEPRECATION")
    private fun openGatt(device: BluetoothDevice): BluetoothGatt? = if (Build.VERSION.SDK_INT >= 37) {
        val settings = BluetoothGattConnectionSettings.Builder()
            .setAutoConnectEnabled(false)
            .setAutomaticMtuEnabled(false)
            .setTransport(BluetoothDevice.TRANSPORT_LE)
            .build()
        device.connectGatt(settings, context.mainExecutor, gattCallback)
    } else {
        device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK, mainHandler)
    }

    private fun message(id: Int, vararg args: Any) = context.getString(id, *args)

    private companion object {
        const val ADDRESS = 0x11
        const val READ_INPUT_REGISTERS = 0x04
        const val READ_HOLDING_REGISTERS = 0x03
        const val REGISTER_COUNT = 80
        const val SCAN_DURATION_MS = 15_000L
        const val READ_TIMEOUT_MS = 8_000L
        const val CONNECTION_TIMEOUT_MS = 15_000L
        const val MAX_RECONNECT_DELAY_MS = 30_000L
        const val DEFAULT_POLL_INTERVAL_MS = 5_000L
        const val CONTROL_VERIFY_DELAY_MS = 300L
        const val CONTROL_CONFIRM_DELAY_MS = 1_000L
        const val CONTROL_CONFIRM_TIMEOUT_MS = 12_000L
        const val DIAGNOSTIC_DELAY_MS = 300L
        const val MAX_RESPONSE_SIZE = 8 + REGISTER_COUNT * 2
        val SERVICE_UUID: UUID = UUID.fromString("0000a002-0000-1000-8000-00805f9b34fb")
        val WRITE_UUID: UUID = UUID.fromString("0000c304-0000-1000-8000-00805f9b34fb")
        val NOTIFY_UUID: UUID = UUID.fromString("0000c305-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
