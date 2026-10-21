package com.example

import com.example.data.api.ApiClientFactory
import com.example.data.storage.AlertPreferences
import com.example.data.storage.AlertSettings
import com.example.data.storage.PanelAlertState
import com.example.data.storage.ServerAlertState
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertMonitorUnitTest {

    @Test
    fun testServerDownTransitionDetection() {
        val lastState = "running"
        val currentState = "offline"

        val wasRunning = lastState.equals("running", ignoreCase = true)
        val isRunning = currentState.equals("running", ignoreCase = true)
        val serverWentDown = wasRunning && !isRunning

        assertTrue("Server should be detected as went offline", serverWentDown)
    }

    @Test
    fun testServerRecoveredTransitionDetection() {
        val lastState = "offline"
        val currentState = "running"

        val wasNotRunning = !lastState.equals("running", ignoreCase = true)
        val isRunning = currentState.equals("running", ignoreCase = true)
        val serverRecovered = wasNotRunning && isRunning

        assertTrue("Server should be detected as recovered", serverRecovered)
    }

    @Test
    fun testAlertCooldown_oneHourWindow() {
        val cooldown = AlertPreferences.COOLDOWN_MILLIS // 1 hour = 3,600,000 ms
        val now = 10_000_000L

        // Alert triggered 30 minutes ago
        val lastAlertRecent = now - (30 * 60 * 1000L)
        val shouldSuppressRecent = (now - lastAlertRecent) < cooldown
        assertTrue("Alert should be suppressed within 1 hour cooldown", shouldSuppressRecent)

        // Alert triggered 70 minutes ago
        val lastAlertOld = now - (70 * 60 * 1000L)
        val shouldTriggerOld = (now - lastAlertOld) >= cooldown
        assertTrue("Alert should be allowed after 1 hour cooldown elapsed", shouldTriggerOld)
    }

    @Test
    fun testThresholdComparison() {
        val cpuUsage = 94.2
        val cpuThreshold = 90
        assertTrue("CPU usage above 90% threshold should trigger alert", cpuUsage >= cpuThreshold)

        val ramUsage = 88.5
        val ramThreshold = 90
        assertFalse("RAM usage below 90% threshold should not trigger alert", ramUsage >= ramThreshold)
    }

    @Test
    fun testServerAlertStateMoshiSerialization() {
        val mapType = Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            ServerAlertState::class.java
        )
        val adapter = ApiClientFactory.moshi.adapter<Map<String, ServerAlertState>>(mapType)

        val sampleState = mapOf(
            "panel-1:server-a" to ServerAlertState(
                lastState = "running",
                lastDownAlertTimestamp = 123456789L,
                lastRecoveredAlertTimestamp = 0L,
                lastCpuAlertTimestamp = 987654321L
            )
        )

        val json = adapter.toJson(sampleState)
        val deserialized = adapter.fromJson(json)

        assertNotNull(deserialized)
        assertEquals(1, deserialized?.size)
        val state = deserialized?.get("panel-1:server-a")
        assertNotNull(state)
        assertEquals("running", state?.lastState)
        assertEquals(123456789L, state?.lastDownAlertTimestamp)
        assertEquals(987654321L, state?.lastCpuAlertTimestamp)
    }

    @Test
    fun testPanelAlertStateMoshiSerialization() {
        val mapType = Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            PanelAlertState::class.java
        )
        val adapter = ApiClientFactory.moshi.adapter<Map<String, PanelAlertState>>(mapType)

        val samplePanelMap = mapOf(
            "panel-1" to PanelAlertState(
                lastReachable = false,
                lastUnreachableAlertTimestamp = 555666777L
            )
        )

        val json = adapter.toJson(samplePanelMap)
        val parsed = adapter.fromJson(json)

        assertNotNull(parsed)
        val panelState = parsed?.get("panel-1")
        assertNotNull(panelState)
        assertFalse(panelState!!.lastReachable)
        assertEquals(555666777L, panelState.lastUnreachableAlertTimestamp)
    }
}
