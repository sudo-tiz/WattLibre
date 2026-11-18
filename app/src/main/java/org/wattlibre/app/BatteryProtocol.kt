package org.wattlibre.app

data class BatteryTelemetry(
    val stateOfChargePercent: Float,
    val expansionBattery1Percent: Float?,
    val expansionBattery2Percent: Float?,
    val acChargingWatts: Int,
    val dcInputWatts: Int,
    val totalInputWatts: Int,
    val inputRegister8Raw: Int,
    val inputRegister9Raw: Int,
    val inputRegister10Raw: Int,
    val acOutputWatts: Int,
    val totalOutputWatts: Int,
    val ledWatts: Float,
    val usbOutputWatts: List<Float>,
    val acOutputVoltage: Float,
    val acOutputFrequency: Float,
    val acInputVoltage: Float,
    val acInputFrequency: Float,
    val ledMode: Int,
    val minutesToFull: Int,
    val minutesToEmpty: Int,
    val modelConstant: Int,
    val systemStateHigh: Int,
    val systemStateLow: Int,
    val inputRegister75Raw: Int,
    val inputRegister76Raw: Int,
    val inputRegister78Raw: Int?,
    val dcConverterActive: Boolean,
    val usbEnabled: Boolean,
    val dcEnabled: Boolean,
    val acEnabled: Boolean,
    val lightEnabled: Boolean,
    val inputRegisters: List<Int> = emptyList(),
)

data class HoldingControlState(
    val holding5Raw: Int,
    val holding11Raw: Int,
    val usb: Int,
    val dc: Int,
    val ac: Int,
    val lightMode: Int,
    val holding30Raw: Int,
    val holding31Raw: Int,
    val holding32Raw: Int,
    val holdingRegisters: List<Int> = emptyList(),
)

enum class OutputControl(val register: Int, val label: String) {
    USB(24, "USB"),
    DC(25, "DC maître"),
    AC(26, "AC"),
    LIGHT(27, "Éclairage"),
}

fun HoldingControlState.rawValue(control: OutputControl) = when (control) {
    OutputControl.USB -> usb
    OutputControl.DC -> dc
    OutputControl.AC -> ac
    OutputControl.LIGHT -> lightMode
}

fun HoldingControlState.isEnabled(control: OutputControl) = rawValue(control) != 0

internal object BatteryProtocol {
    private const val ADDRESS = 0x11
    private const val READ_INPUT_REGISTERS = 0x04
    private const val REGISTER_COUNT = 80

    fun readInputRegisters(start: Int = 0, count: Int = REGISTER_COUNT): ByteArray {
        return readRegisters(READ_INPUT_REGISTERS, start, count)
    }

    fun readHoldingRegisters(start: Int = 0, count: Int = REGISTER_COUNT): ByteArray {
        return readRegisters(0x03, start, count)
    }

    private fun readRegisters(function: Int, start: Int, count: Int): ByteArray {
        require(start in 0 until REGISTER_COUNT && count in 1..REGISTER_COUNT - start)
        val request = byteArrayOf(
            ADDRESS.toByte(), function.toByte(),
            (start shr 8).toByte(), start.toByte(),
            (count shr 8).toByte(), count.toByte(),
        )
        return withCrc(request)
    }

    fun writeOutputControl(control: OutputControl, enabled: Boolean): ByteArray {
        return writeRegister(control.register, if (enabled) 1 else 0)
    }

    fun writeLightMode(mode: Int): ByteArray {
        require(mode in 0..3)
        return writeRegister(OutputControl.LIGHT.register, mode)
    }

    private fun writeRegister(register: Int, value: Int): ByteArray {
        val request = byteArrayOf(
            ADDRESS.toByte(), 0x06,
            (register shr 8).toByte(), register.toByte(),
            (value shr 8).toByte(), value.toByte(),
        )
        return withCrc(request)
    }

    fun parseInputResponse(frame: ByteArray): BatteryTelemetry? {
        val registers = parseRegisters(frame, READ_INPUT_REGISTERS, minCount = 79) ?: return null
        fun register(index: Int) = registers[index]
        val soc = register(56)
        if (soc > 1000) return null
        fun extensionSoc(index: Int): Float? = register(index).takeIf { it in 10..1010 }?.let { it / 10f - 1f }
        val status = register(41)

        return BatteryTelemetry(
            stateOfChargePercent = soc / 10f,
            expansionBattery1Percent = extensionSoc(53),
            expansionBattery2Percent = extensionSoc(55),
            acChargingWatts = register(3),
            dcInputWatts = register(4),
            totalInputWatts = register(6),
            inputRegister8Raw = register(8),
            inputRegister9Raw = register(9),
            inputRegister10Raw = register(10),
            acOutputWatts = register(20),
            totalOutputWatts = register(39),
            ledWatts = register(15) / 10f,
            usbOutputWatts = listOf(30, 31, 34, 35, 36, 37).map { register(it) / 10f },
            acOutputVoltage = register(18) / 10f,
            acOutputFrequency = register(19) / 10f,
            acInputVoltage = register(21) / 10f,
            acInputFrequency = register(22) / 100f,
            ledMode = register(25),
            minutesToFull = register(58),
            minutesToEmpty = register(59),
            modelConstant = register(52),
            systemStateHigh = status,
            systemStateLow = register(42),
            inputRegister75Raw = register(75),
            inputRegister76Raw = register(76),
            inputRegister78Raw = registers.getOrNull(78),
            dcConverterActive = (status and 0x0080) != 0,
            usbEnabled = (status and 0x0200) != 0,
            dcEnabled = (status and 0x0400) != 0,
            acEnabled = (status and 0x0800) != 0,
            lightEnabled = (status and 0x1000) != 0,
            inputRegisters = registers.toList(),
        )
    }

    fun parseHoldingControlState(frame: ByteArray): HoldingControlState? {
        val registers = parseRegisters(frame, 0x03, minCount = 33) ?: return null
        return HoldingControlState(
            registers[5], registers[11], registers[24], registers[25], registers[26], registers[27],
            registers[30], registers[31], registers[32], registers.toList(),
        )
    }

    private fun parseRegisters(frame: ByteArray, function: Int, minCount: Int): IntArray? {
        if (frame.size < 8 || frame[0].u8() != ADDRESS || frame[1].u8() != function) return null
        val count = frame.u16(4)
        if (frame.u16(2) != 0 || count !in minCount..REGISTER_COUNT || frame.size != 8 + count * 2) return null
        if (crc16(frame, frame.size - 2) != frame.u16(frame.size - 2)) return null
        return IntArray(count) { frame.u16(6 + it * 2) }
    }

    private fun crc16(bytes: ByteArray, length: Int): Int {
        var crc = 0xFFFF
        for (index in 0 until length) {
            crc = crc xor bytes[index].u8()
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
        }
        return crc
    }

    private fun withCrc(payload: ByteArray): ByteArray {
        val crc = crc16(payload, payload.size)
        return payload + byteArrayOf((crc shr 8).toByte(), crc.toByte())
    }

    private fun Byte.u8() = toInt() and 0xFF
    private fun ByteArray.u16(offset: Int) = (this[offset].u8() shl 8) or this[offset + 1].u8()
}
