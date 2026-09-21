package com.tonapps.tonkeeper.ui.screen.battery.web

import com.tonapps.log.L
import com.tonapps.log.LogTarget
import com.tonapps.log.LoggerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.coroutines.EmptyCoroutineContext

class BatteryWebBridgeTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val logs = mutableListOf<String>()

    @Before
    fun setUpLogger() {
        L.initialize(
            LoggerConfig(
                logsDir = temporaryFolder.root,
                sharedDir = temporaryFolder.root,
                pubKeyProvider = { null },
                executor = { it.run() },
                scope = CoroutineScope(EmptyCoroutineContext),
            ),
            emptyList(),
        )
        L.setTargets(listOf(object : LogTarget {
            override fun log(type: L.LogType, tag: String?, msg: String?) {
                logs += msg.orEmpty()
            }
        }))
    }

    @After
    fun resetLogger() {
        L.setTargets(emptyList())
    }

    @Test
    fun logsRequestProgressWithoutCredentials() = runBlocking {
        val bridge = BatteryWebBridge {
            BatteryWebBridge.Data("private-device-id", "private-device-token", "private-proof-token")
        }

        bridge.handleMessage("not json: private-malformed-token")
        bridge.handleMessage(
            """{"type":"refresh-data","queryId":"query-3","accessToken":"private-expired-token"}"""
        )

        val output = logs.joinToString("\n")
        assertTrue(output.contains("invalid JSON"))
        assertTrue(output.contains("method=refresh-data, queryId=query-3"))
        assertTrue(output.contains("Response prepared"))
        assertTrue(output.contains("hasDeviceToken=true"))
        assertFalse(output.contains("private-"))
    }

    @Test
    fun getDataReturnsCorrelatedResponseWithStringPayload() = runBlocking {
        val bridge = BatteryWebBridge { expiredAccessToken ->
            assertNull(expiredAccessToken)
            BatteryWebBridge.Data("device", "current-token", "proof")
        }

        val response = Json.parseToJsonElement(bridge.handleMessage(
            """{"type":"get-data","queryId":"query-1"}"""
        )!!).jsonObject

        assertEquals(setOf("queryId", "payload"), response.keys)
        assertEquals("query-1", response.getValue("queryId").jsonPrimitive.content)
        val payload = response.getValue("payload").jsonPrimitive
        assertTrue(payload.isString)
        assertEquals(
            Json.parseToJsonElement("""{"deviceId":"device","deviceToken":"current-token","proofToken":"proof"}"""),
            Json.parseToJsonElement(payload.content),
        )
    }

    @Test
    fun refreshDataPassesExpiredTokenAndReturnsRefreshedData() = runBlocking {
        val bridge = BatteryWebBridge { expiredAccessToken ->
            assertEquals("expired-token", expiredAccessToken)
            BatteryWebBridge.Data("device", "new-token", "proof")
        }

        val response = Json.parseToJsonElement(bridge.handleMessage(
            """{"type":"refresh-data","queryId":"query-2","accessToken":"expired-token"}"""
        )!!).jsonObject

        assertEquals("query-2", response.getValue("queryId").jsonPrimitive.content)
        val payload = Json.parseToJsonElement(response.getValue("payload").jsonPrimitive.content).jsonObject
        assertEquals("new-token", payload.getValue("deviceToken").jsonPrimitive.content)
    }

    @Test
    fun ignoresMalformedAndUnrelatedMessagesWithoutLoadingCredentials() = runBlocking {
        val bridge = BatteryWebBridge { error("Credentials must not be loaded") }

        listOf(
            "not json",
            "[]",
            """{"type":"invokeRnFunc","queryId":"query"}""",
            """{"type":"get-data"}""",
            """{"type":"get-data","queryId":" "}""",
            """{"type":"get-data","queryId":123}""",
            """{"type":"refresh-data","queryId":"query"}""",
            """{"type":"refresh-data","queryId":"query","accessToken":""}""",
            """{"type":"refresh-data","queryId":"query","accessToken":null}""",
            """{"type":"refresh-data","queryId":"query","accessToken":123}""",
        ).forEach { assertNull(bridge.handleMessage(it)) }
    }

    @Test
    fun escapesQueryIdAndPayloadStrings() = runBlocking {
        val proof = "proof\"\\\n"
        val bridge = BatteryWebBridge { BatteryWebBridge.Data("", "", proof) }
        val response = Json.parseToJsonElement(bridge.handleMessage(
            """{"type":"get-data","queryId":"query\"\\\n"}"""
        )!!).jsonObject

        assertEquals("query\"\\\n", response.getValue("queryId").jsonPrimitive.content)
        val payload = Json.parseToJsonElement(response.getValue("payload").jsonPrimitive.content).jsonObject
        assertEquals(proof, payload.getValue("proofToken").jsonPrimitive.content)
        assertEquals("", payload.getValue("deviceToken").jsonPrimitive.content)
    }
}
