package com.garagepi.telemetry.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UdsResponseParserTest {

    @Test
    fun `strips the response header`() {
        val data = UdsResponseParser.parseData("62 01 01 AB CD")!!
        assertEquals(2, data.size)
        assertEquals(0xAB.toByte(), data[0])
    }

    @Test
    fun `rejects a response that is not a positive mode 22 reply`() {
        assertNull(UdsResponseParser.parseData("7F 22 31"))
        assertNull(UdsResponseParser.parseData("NO DATA"))
        assertNull(UdsResponseParser.parseData(""))
    }

    @Test
    fun `trims ISO-TP padding to the declared length`() {
        // Declared 0x08 = 8 bytes; everything after is AA filler, not data.
        val raw = "008 0: 62 E0 04 01 02 03 1: 04 05 AA AA AA AA AA"
        val data = UdsResponseParser.parseData(raw)!!
        assertEquals("should keep 8 declared bytes minus the 3-byte header", 5, data.size)
        assertTrue("no padding should survive", data.none { it == 0xAA.toByte() })
    }

    @Test
    fun `a declared length longer than the response does not invent bytes`() {
        val raw = "0FF 0: 62 E0 04 01 02 03"
        val data = UdsResponseParser.parseData(raw)!!
        assertEquals(3, data.size)
    }

    @Test
    fun `real captured frame keeps its payload intact`() {
        // 220105 from the car: declared 0x2E = 46, with two trailing AA bytes.
        val raw = "02E 0: 62 01 05 FF FB 74 1: 0F 01 2C 01 01 2C 1D 2: 1E 1D 1E 1D 1D 1D 6C " +
            "3: 34 6C 34 00 00 4B 20 4: 00 03 95 7E 40 E6 00 5: 6D 00 00 00 00 00 00 " +
            "6: 00 1D 1D 1E 1E AA AA"
        val data = UdsResponseParser.parseData(raw)!!
        assertEquals("46 declared minus the 3-byte header", 43, data.size)
        // Display SOC at letter af must survive the trim.
        assertEquals(54.5, data[31].toInt().and(0xFF) / 2.0, 0.001)
    }
}

class VmcuSpeedTest {

    private fun payload(vararg bytes: Int) = bytes.map { it.toByte() }.toByteArray()

    /** Bytes 11-12 big-endian, /100. */
    private fun frameWithSpeed(hi: Int, lo: Int) =
        payload(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, hi, lo, 0)

    @Test
    fun `decodes a normal speed`() {
        // 0x1964 = 6500 -> 65.0 mph
        val out = IoniqUds.decodeVmcuSpeed(frameWithSpeed(0x19, 0x64))
        assertEquals(65.0, out["SPEED_VMCU"]!!, 0.001)
    }

    @Test
    fun `rejects ISO-TP padding read as speed`() {
        // The exact failure seen in the logs: 0xAAAA / 100 = 436.9 mph.
        val out = IoniqUds.decodeVmcuSpeed(frameWithSpeed(0xAA, 0xAA))
        assertTrue("padding must not be published as a reading", out.isEmpty())
    }

    @Test
    fun `rejects anything above the car's capability`() {
        // Tops out near 115 mph, so 150 is a generous ceiling no real reading reaches.
        assertTrue(IoniqUds.decodeVmcuSpeed(frameWithSpeed(0x3A, 0xF2)).isEmpty()) // 151.22
        assertTrue(IoniqUds.decodeVmcuSpeed(frameWithSpeed(0xFF, 0xFF)).isEmpty()) // 655.35
    }

    @Test
    fun `the ceiling itself is kept — the rule is over 150, not 150 and above`() {
        val out = IoniqUds.decodeVmcuSpeed(frameWithSpeed(0x3A, 0x98)) // exactly 150.00
        assertEquals(150.0, out["SPEED_VMCU"]!!, 0.001)
    }

    @Test
    fun `keeps a high but achievable speed`() {
        // 0x2CEC = 11500 -> 115.0 mph, the car's actual top speed. Must not be dropped.
        val out = IoniqUds.decodeVmcuSpeed(frameWithSpeed(0x2C, 0xEC))
        assertEquals(115.0, out["SPEED_VMCU"]!!, 0.001)
    }

    @Test
    fun `a short frame yields nothing rather than reading past the end`() {
        assertTrue(IoniqUds.decodeVmcuSpeed(payload(0x62, 0x01)).isEmpty())
    }
}
