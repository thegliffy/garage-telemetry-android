package com.garagepi.telemetry.sync

import com.garagepi.telemetry.obd.TelemetryFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PidMapTest {

    @Test
    fun `oversize energy pid matches garagepi short code`() {
        assertEquals("REM_KWH", PidMap.toApiPid("REMAINING_ENERGY_KWH"))
    }

    @Test
    fun `oversize cluster speed is shortened`() {
        assertEquals("SPD_CL_KMH", PidMap.toApiPid("SPEED_CLUSTER_KMH"))
        assertTrue(PidMap.toApiPid("SPEED_CLUSTER_KMH")!!.length <= PidMap.MAX_PID_LENGTH)
    }

    @Test
    fun `pack fields match garagepi Grafana series`() {
        assertEquals("PACK_V", PidMap.toApiPid("PACK_VOLTAGE_V"))
        assertEquals("PACK_A", PidMap.toApiPid("PACK_CURRENT_A"))
        assertEquals("PACK_KW", PidMap.toApiPid("PACK_POWER_KW"))
        assertEquals("BATT_TMAX", PidMap.toApiPid("BATT_TEMP_MAX_C"))
        assertEquals("DCHG_KW", PidMap.toApiPid("MAX_POWER_KW"))
        assertEquals("CHG_KW", PidMap.toApiPid("MAX_REGEN_KW"))
    }

    @Test
    fun `vmcu speed is not written as 010D`() {
        assertEquals("SPEED_VMCU", PidMap.toApiPid("SPEED_VMCU"))
    }

    @Test
    fun `unknown oversize pid is dropped rather than 422 the batch`() {
        assertNull(PidMap.toApiPid("THIS_PID_IS_WAY_TOO_LONG"))
    }

    @Test
    fun `unknown short pid passes through`() {
        assertEquals("HV_SOC", PidMap.toApiPid("HV_SOC"))
        assertEquals("FOO", PidMap.toApiPid("FOO"))
    }

    @Test
    fun `every mapped api pid fits the ingest max`() {
        val tooLong = PidMap.LOCAL_TO_API.filter { it.value.length > PidMap.MAX_PID_LENGTH }
        assertTrue("oversize API pids: $tooLong", tooLong.isEmpty())
    }

    @Test
    fun `every TelemetryField pid maps to a legal API pid`() {
        val bad = TelemetryFields.ALL.mapNotNull { field ->
            val api = PidMap.toApiPid(field.pid)
            if (api == null || api.length > PidMap.MAX_PID_LENGTH) field.pid else null
        }
        assertTrue("unmapped or oversize: $bad", bad.isEmpty())
    }
}
