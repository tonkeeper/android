package com.tonapps.tonkeeper.manager.tonconnect.bridge

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonBuilderSignMessageTest {

    @Test
    fun advertisesSignMessageWhenWalletCanSignInternalMessages() {
        val device = JsonBuilder.device(255, "5.0.0", signMessageMaxMessages = 255)

        val feature = featureNamed(device, "SignMessage")
        assertTrue(feature != null)
        assertEquals(255, feature!!.getInt("maxMessages"))
        assertEquals(255, featureNamed(device, "SendTransaction")!!.getInt("maxMessages"))
    }

    @Test
    fun hidesSignMessageWhenWalletCannotSignInternalMessages() {
        val device = JsonBuilder.device(255, "5.0.0", signMessageMaxMessages = null)

        assertFalse(featureNamed(device, "SignMessage") != null)
    }

    @Test
    fun signMessageResponseWrapsInternalBocInObject() {
        val response = JsonBuilder.responseSignMessage(7, "te6cc")

        assertEquals(7L, response.getLong("id"))
        assertEquals("te6cc", response.getJSONObject("result").getString("internalBoc"))
    }

    private fun featureNamed(device: JSONObject, name: String): JSONObject? {
        val features = device.getJSONArray("features")
        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            if (feature.getString("name") == name) {
                return feature
            }
        }
        return null
    }
}
