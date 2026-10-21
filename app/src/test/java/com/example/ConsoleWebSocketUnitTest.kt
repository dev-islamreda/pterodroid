package com.example

import com.example.data.api.ApiClientFactory
import com.example.data.model.ResourceStats
import com.example.data.model.ServerAttributes
import com.example.data.websocket.WsMessage
import com.example.ui.console.TerminalLogFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleWebSocketUnitTest {

    private val wsAdapter = ApiClientFactory.moshi.adapter(WsMessage::class.java)
    private val resourceAdapter = ApiClientFactory.moshi.adapter(ResourceStats::class.java)

    @Test
    fun testParseAuthSuccessEvent() {
        val json = """{"event":"auth success","args":null}"""
        val msg = wsAdapter.fromJson(json)
        assertNotNull(msg)
        assertEquals("auth success", msg?.event)
    }

    @Test
    fun testParseStatusChangeEvent() {
        val json = """{"event":"status","args":["running"]}"""
        val msg = wsAdapter.fromJson(json)
        assertNotNull(msg)
        assertEquals("status", msg?.event)
        assertEquals("running", msg?.args?.firstOrNull())
    }

    @Test
    fun testParseConsoleOutputEvent() {
        val json = """{"event":"console output","args":["[12:34:56 INFO]: Server started on port 25565"]}"""
        val msg = wsAdapter.fromJson(json)
        assertNotNull(msg)
        assertEquals("console output", msg?.event)
        assertEquals("[12:34:56 INFO]: Server started on port 25565", msg?.args?.firstOrNull())
    }

    @Test
    fun testParseStatsUpdateEvent_withNestedNetwork() {
        val statsInnerJson = """
            {
                "memory_bytes": 2147483648,
                "memory_limit_bytes": 4294967296,
                "cpu_absolute": 25.4,
                "network": {
                    "rx_bytes": 1048576,
                    "tx_bytes": 2097152
                },
                "state": "running",
                "disk_bytes": 10737418240
            }
        """.trimIndent()

        val json = """{"event":"stats","args":["${statsInnerJson.replace("\"", "\\\"").replace("\n", "")}"]}"""
        val msg = wsAdapter.fromJson(json)
        assertNotNull(msg)
        assertEquals("stats", msg?.event)

        val stats = resourceAdapter.fromJson(msg!!.args!!.first()!!)
        assertNotNull(stats)
        assertEquals(2147483648L, stats?.memoryBytes)
        assertEquals(4294967296L, stats?.memoryLimitBytes)
        assertEquals(25.4, stats?.cpuAbsolute ?: 0.0, 0.01)
        assertEquals(1048576L, stats?.effectiveRxBytes)
        assertEquals(2097152L, stats?.effectiveTxBytes)
        assertEquals(10737418240L, stats?.diskBytes)
        assertEquals("running", stats?.state)
    }

    @Test
    fun testParseTokenExpiringAndExpiredEvents() {
        val expiringJson = """{"event":"token expiring","args":null}"""
        val expiringMsg = wsAdapter.fromJson(expiringJson)
        assertEquals("token expiring", expiringMsg?.event)

        val expiredJson = """{"event":"token expired","args":null}"""
        val expiredMsg = wsAdapter.fromJson(expiredJson)
        assertEquals("token expired", expiredMsg?.event)
    }

    @Test
    fun testParseJwtAndDaemonErrors() {
        val jwtErrorJson = """{"event":"jwt error","args":["The provided token has expired or is invalid."]}"""
        val jwtMsg = wsAdapter.fromJson(jwtErrorJson)
        assertEquals("jwt error", jwtMsg?.event)
        assertEquals("The provided token has expired or is invalid.", jwtMsg?.args?.firstOrNull())
    }

    @Test
    fun testOutgoingAuthMessageSerialization() {
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.dummyPayload.signature"
        val msg = WsMessage(event = "auth", args = listOf(token))
        val json = wsAdapter.toJson(msg)
        assertTrue(json.contains("\"event\":\"auth\""))
        assertTrue(json.contains(token))
    }

    @Test
    fun testOutgoingSendCommandMessageSerialization() {
        val msg = WsMessage(event = "send command", args = listOf("say Server restarting in 5 minutes"))
        val json = wsAdapter.toJson(msg)
        assertTrue(json.contains("\"event\":\"send command\""))
        assertTrue(json.contains("say Server restarting in 5 minutes"))
    }

    @Test
    fun testOutgoingPowerStateMessageSerialization() {
        val msg = WsMessage(event = "set state", args = listOf("restart"))
        val json = wsAdapter.toJson(msg)
        assertTrue(json.contains("\"event\":\"set state\""))
        assertTrue(json.contains("\"restart\""))
    }

    @Test
    fun testDestructivePowerActionIdentification() {
        fun isDestructive(signal: String): Boolean {
            return signal.equals("restart", ignoreCase = true) ||
                    signal.equals("stop", ignoreCase = true) ||
                    signal.equals("kill", ignoreCase = true)
        }

        assertTrue("Restart must be flagged as destructive", isDestructive("restart"))
        assertTrue("Stop must be flagged as destructive", isDestructive("stop"))
        assertTrue("Kill must be flagged as destructive", isDestructive("kill"))
        assertFalse("Start is not destructive", isDestructive("start"))
    }

    @Test
    fun testServerOwnerPermissions_alwaysAllowed() {
        val ownerServer = ServerAttributes(
            serverOwner = true,
            identifier = "srv-101",
            name = "Owner Server",
            permissions = emptyList()
        )
        assertTrue(ownerServer.canSendCommands())
        assertTrue(ownerServer.canControlPower("start"))
        assertTrue(ownerServer.canControlPower("restart"))
        assertTrue(ownerServer.canControlPower("stop"))
    }

    @Test
    fun testSubuserPermissions_controlConsole() {
        val subuserWithConsole = ServerAttributes(
            serverOwner = false,
            identifier = "srv-102",
            name = "Subuser Server",
            permissions = listOf("control.console", "control.start")
        )
        assertTrue(subuserWithConsole.canSendCommands())
        assertTrue(subuserWithConsole.canControlPower("start"))
        assertFalse(subuserWithConsole.canControlPower("stop"))
        assertFalse(subuserWithConsole.canControlPower("restart"))
    }

    @Test
    fun testTerminalLogFormatter_cleansAnsiEscapeCodes() {
        val rawLog = "\u001B[32m[12:00:00 INFO]\u001B[0m: Server \u001B[1mready\u001B[0m!\r\n"
        val cleaned = TerminalLogFormatter.cleanAnsi(rawLog)
        assertEquals("[12:00:00 INFO]: Server ready!", cleaned)
        assertFalse(cleaned.contains("\u001B"))
        assertFalse(cleaned.contains("\r"))
    }

    @Test
    fun testConsoleLogs_exportAsJson() {
        val sampleLogs = listOf(
            "\u001B[32m[12:00:00 INFO]\u001B[0m: Server started",
            "[12:00:01 INFO]: Listening on port 25565"
        )
        val cleanLogs = sampleLogs.map { TerminalLogFormatter.cleanAnsi(it) }
        val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, String::class.java)
        val adapter = ApiClientFactory.moshi.adapter<List<String>>(type).indent("  ")
        val jsonString = adapter.toJson(cleanLogs)
        val parsed = adapter.fromJson(jsonString)
        assertNotNull(parsed)
        assertEquals(2, parsed?.size)
        assertEquals("[12:00:00 INFO]: Server started", parsed?.get(0))
        assertEquals("[12:00:01 INFO]: Listening on port 25565", parsed?.get(1))
    }

    @Test
    fun testConsoleLogs_copyAsPlainText() {
        val sampleLogs = listOf(
            "\u001B[32m[12:00:00 INFO]\u001B[0m: First line\r",
            "[12:00:01 INFO]: Second line"
        )
        val plainText = sampleLogs.joinToString("\n") { TerminalLogFormatter.cleanAnsi(it) }
        assertEquals("[12:00:00 INFO]: First line\n[12:00:01 INFO]: Second line", plainText)
    }
}
