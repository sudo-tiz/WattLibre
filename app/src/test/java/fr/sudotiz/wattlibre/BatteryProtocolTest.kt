package fr.sudotiz.wattlibre

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryProtocolTest {
    @Test
    fun readRequestMatchesDocumentedCrcVector() {
        assertArrayEquals(
            byteArrayOf(0x11, 0x04, 0x00, 0x3A, 0x00, 0x03, 0x96.toByte(), 0x92.toByte()),
            BatteryProtocol.readInputRegisters(start = 58, count = 3),
        )
    }

    @Test
    fun controlWriteUsesOnlyKnownOutputRegisterEncoding() {
        assertArrayEquals(
            byteArrayOf(0x11, 0x06, 0, 0x18, 0, 1, 0x9D.toByte(), 0xCA.toByte()),
            BatteryProtocol.writeOutputControl(OutputControl.USB, true),
        )
    }

    @Test
    fun dcOffPacketTargetsHoldingRegister25() {
        val packet = BatteryProtocol.writeOutputControl(OutputControl.DC, false)
        assertArrayEquals(byteArrayOf(0x11, 0x06, 0, 0x19, 0, 0), packet.copyOfRange(0, 6))
        val crc = modbusCrc(packet, 6)
        assertEquals((crc shr 8) and 0xFF, packet[6].toInt() and 0xFF)
        assertEquals(crc and 0xFF, packet[7].toInt() and 0xFF)
    }

    @Test
    fun lightModeWriteTargetsHoldingRegister27() {
        val packet = BatteryProtocol.writeLightMode(3)
        assertArrayEquals(byteArrayOf(0x11, 0x06, 0, 27, 0, 3), packet.copyOfRange(0, 6))
        val crc = modbusCrc(packet, 6)
        assertEquals((crc shr 8) and 0xFF, packet[6].toInt() and 0xFF)
        assertEquals(crc and 0xFF, packet[7].toInt() and 0xFF)
    }

    @Test
    fun parsesTelemetryAndRejectsBadCrc() {
        val frame = ByteArray(168)
        frame[0] = 0x11
        frame[1] = 0x04
        frame[4] = 0x00
        frame[5] = 0x50
        fun setRegister(index: Int, value: Int) {
            frame[6 + index * 2] = (value shr 8).toByte()
            frame[7 + index * 2] = value.toByte()
        }
        setRegister(3, 180)
        setRegister(4, 20)
        setRegister(6, 200)
        setRegister(39, 150)
        setRegister(8, 123)
        setRegister(9, 456)
        setRegister(10, 789)
        setRegister(41, 0x0C80)
        setRegister(42, 0x6000)
        setRegister(53, 504)
        setRegister(55, 0)
        setRegister(56, 435)
        setRegister(30, 125)
        setRegister(58, 60)
        setRegister(59, 120)
        setRegister(75, 0x0800)
        setRegister(76, 0x0003)
        setRegister(78, 987)
        val crc = modbusCrc(frame, frame.size - 2)
        frame[frame.lastIndex - 1] = (crc shr 8).toByte()
        frame[frame.lastIndex] = crc.toByte()

        val telemetry = requireNotNull(BatteryProtocol.parseInputResponse(frame))
        assertEquals(43.5f, telemetry.stateOfChargePercent, 0f)
        assertEquals(200, telemetry.totalInputWatts)
        assertEquals(150, telemetry.totalOutputWatts)
        assertEquals(50, telemetry.netPowerWatts)
        assertEquals(-10, telemetry.copy(totalOutputWatts = 210).netPowerWatts)
        assertEquals(0, telemetry.copy(totalOutputWatts = 200).netPowerWatts)
        assertEquals(123, telemetry.inputRegister8Raw)
        assertEquals(456, telemetry.inputRegister9Raw)
        assertEquals(789, telemetry.inputRegister10Raw)
        assertEquals(120, telemetry.minutesToEmpty)
        assertEquals(987, telemetry.inputRegister78Raw)
        assertEquals(0x0800, telemetry.inputRegister75Raw)
        assertEquals(0x0003, telemetry.inputRegister76Raw)
        assertEquals(80, telemetry.inputRegisters.size)
        assertEquals(789, telemetry.inputRegisters[10])
        assertEquals(49.4f, telemetry.expansionBattery1Percent!!, 0.001f)
        assertNull(telemetry.expansionBattery2Percent)
        assertEquals(12.5f, telemetry.usbOutputWatts.first(), 0f)
        assertEquals(true, telemetry.acEnabled)
        assertEquals(true, telemetry.dcConverterActive)
        frame[frame.lastIndex] = (frame.last().toInt() xor 1).toByte()
        assertNull(BatteryProtocol.parseInputResponse(frame))
    }

    @Test
    fun parsesDcHoldingRegisterWithoutWriting() {
        val frame = ByteArray(168)
        frame[0] = 0x11
        frame[1] = 0x03
        frame[5] = 0x50
        frame[6 + 24 * 2 + 1] = 1
        frame[6 + 25 * 2 + 1] = 1
        frame[6 + 26 * 2 + 1] = 0
        frame[6 + 27 * 2 + 1] = 3
        frame[6 + 30 * 2 + 1] = 1
        frame[6 + 31 * 2 + 1] = 0
        frame[6 + 32 * 2 + 1] = 1
        val crc = modbusCrc(frame, frame.size - 2)
        frame[frame.lastIndex - 1] = (crc shr 8).toByte()
        frame[frame.lastIndex] = crc.toByte()

        val state = requireNotNull(BatteryProtocol.parseHoldingControlState(frame))
        assertEquals(1, state.usb)
        assertEquals(1, state.dc)
        assertEquals(0, state.ac)
        assertEquals(3, state.lightMode)
        assertEquals(1, state.holding30Raw)
        assertEquals(0, state.holding31Raw)
        assertEquals(1, state.holding32Raw)
        assertEquals(80, state.holdingRegisters.size)
        assertEquals(3, state.holdingRegisters[27])
    }

    private fun modbusCrc(bytes: ByteArray, length: Int): Int {
        var crc = 0xFFFF
        repeat(length) { index ->
            crc = crc xor (bytes[index].toInt() and 0xFF)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
        }
        return crc
    }
}
