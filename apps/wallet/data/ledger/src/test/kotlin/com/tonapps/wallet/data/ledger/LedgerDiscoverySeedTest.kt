package com.tonapps.wallet.data.ledger

import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.apdu.ApduResponse
import com.tonapps.ledgerkit.controller.LedgerController
import com.tonapps.ledgerkit.controller.LedgerOperationState
import com.tonapps.ledgerkit.transport.LedgerLink
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.ledgerkit.transport.LedgerTransport
import com.tonapps.wallet.data.ledger.discovery.LedgerAppDiscoveryState
import com.tonapps.wallet.data.ledger.discovery.LedgerDevice
import com.tonapps.wallet.data.ledger.discovery.LedgerDiscoveryInteractorImpl
import com.tonapps.wallet.data.ledger.discovery.LedgerDiscoveryResult
import com.trustwallet.core.AnyAddress
import com.trustwallet.core.CoinType
import com.trustwallet.core.WalletCoreLibLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ton.kotlin.crypto.PrivateKeyEd25519
import kotlin.time.Duration
import com.trustwallet.core.PrivateKey as TwPrivateKey

class LedgerDiscoverySeedTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val device = SeedSwitchingDevice()
    private val controller = LedgerController(device, scope)
    private var failures = 0

    @Before
    fun setUp() {
        WalletCoreLibLoader.loadLibrary()
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `a lock after the ton read with the same pin still discovers every app`() = runBlocking {
        device.lockAfterTonRead = true
        val result = discover { controller.dismiss() }
        assertEquals(mapOf(LedgerChainApp.Ton to LedgerAppDiscoveryState.Done, LedgerChainApp.Ethereum to LedgerAppDiscoveryState.Done), result.apps)
        assertEquals(device.ethAddress(PRIMARY).lowercase(), result.evmAddress())
    }

    @Test
    fun `unlocking with another pin after the ton read is a ton address mismatch`() = runBlocking {
        device.lockAfterTonRead = true
        device.seedOnUnlock = SECONDARY
        assertEquals(LedgerFailure.AddressMismatch(LedgerAppId.Ton), failureOf { discover { controller.dismiss() } })
    }

    @Test
    fun `a retry after unlocking with another pin is a ton address mismatch`() = runBlocking {
        device.lockOnFirstEthRead = true
        val failure = failureOf {
            discover { state ->
                if (state.failure == LedgerFailure.DeviceLocked && failures == 1) {
                    device.seed = SECONDARY
                    device.current = DASHBOARD
                    controller.retry()
                } else {
                    controller.dismiss()
                }
            }
        }
        assertEquals(LedgerFailure.AddressMismatch(LedgerAppId.Ton), failure)
        assertEquals(2, failures)
    }

    @Test
    fun `a retry with the same pin finishes on the same seed`() = runBlocking {
        device.lockOnFirstEthRead = true
        val result = discover { state ->
            if (state.failure == LedgerFailure.DeviceLocked && failures == 1) {
                controller.retry()
            } else {
                controller.dismiss()
            }
        }
        assertEquals(device.ethAddress(PRIMARY).lowercase(), result.evmAddress())
        assertEquals(1, failures)
    }

    private suspend fun discover(onFailed: (LedgerOperationState.Failed) -> Unit): LedgerDiscoveryResult {
        val watcher = scope.launch {
            controller.state.collect { state ->
                if (state is LedgerOperationState.Failed) {
                    failures++
                    onFailed(state)
                }
            }
        }
        return try {
            LedgerDiscoveryInteractorImpl(controller).discover(
                device = LedgerDevice("fake", "Fake Ledger", LedgerModel.Stax),
                indices = listOf(0),
                apps = listOf(LedgerChainApp.Ton, LedgerChainApp.Ethereum),
            )
        } finally {
            watcher.cancel()
        }
    }

    private suspend fun failureOf(block: suspend () -> Unit): LedgerFailure? = try {
        block()
        null
    } catch (e: LedgerFailureException) {
        e.failure
    }

    private fun LedgerDiscoveryResult.evmAddress(): String =
        accounts.single().accounts.first { it.chain == Chain.Ethereum.Mainnet }.displayAddress.lowercase()

    private class SeedSwitchingDevice : LedgerTransport {

        var seed = PRIMARY
        var seedOnUnlock = PRIMARY
        var current = DASHBOARD
        var lockAfterTonRead = false
        var lockOnFirstEthRead = false
        private var lockedPolls = 0

        fun ethAddress(seed: Int): String = AnyAddress(ethPrivateKey(seed).getPublicKeySecp256k1(false), CoinType.Ethereum).description

        override suspend fun connect(deviceId: String, onProgress: (LedgerProgress) -> Unit): LedgerLink = object : LedgerLink {
            private var closed = false
            override val deviceId: String = deviceId
            override val model: LedgerModel = LedgerModel.Stax
            override val isConnected: Boolean
                get() = !closed

            override suspend fun exchange(apdu: ByteArray, timeout: Duration?): ApduResponse = handle(apdu)

            override suspend fun awaitDisconnected() {
                closed = true
            }

            override fun close() {
                closed = true
            }
        }

        private fun handle(apdu: ByteArray): ApduResponse {
            val cla = apdu[0].toInt() and 0xff
            val ins = apdu[1].toInt() and 0xff
            return when {
                cla == 0xb0 && ins == 0x01 -> appAndVersion()
                cla == 0xb0 && ins == 0xa7 -> {
                    current = DASHBOARD
                    ApduResponse(ByteArray(0), 0x9000)
                }
                cla == 0xe0 && ins == 0xd8 -> {
                    current = String(apdu.copyOfRange(5, apdu.size))
                    ApduResponse(ByteArray(0), 0x9000)
                }
                current == LedgerAppId.Ton.openName && ins == 0x05 -> {
                    if (lockAfterTonRead) {
                        lockAfterTonRead = false
                        lockedPolls = 1
                    }
                    ApduResponse(tonPublicKey(seed), 0x9000)
                }
                current == LedgerAppId.Ethereum.openName && ins == 0x02 -> {
                    if (lockOnFirstEthRead) {
                        lockOnFirstEthRead = false
                        return ApduResponse(ByteArray(0), 0x5515)
                    }
                    val publicKey = ethPrivateKey(seed).getPublicKeySecp256k1(false).data
                    val address = ethAddress(seed).removePrefix("0x").toByteArray()
                    ApduResponse(byteArrayOf(publicKey.size.toByte()) + publicKey + byteArrayOf(address.size.toByte()) + address, 0x9000)
                }
                else -> ApduResponse(ByteArray(0), 0x6d00)
            }
        }

        private fun appAndVersion(): ApduResponse {
            if (lockedPolls > 0) {
                lockedPolls--
                if (lockedPolls == 0 && seedOnUnlock != seed) {
                    seed = seedOnUnlock
                    current = DASHBOARD
                }
                return ApduResponse(ByteArray(0), 0x5515)
            }
            val name = current.toByteArray()
            val version = "9.9.9".toByteArray()
            return ApduResponse(byteArrayOf(1, name.size.toByte()) + name + byteArrayOf(version.size.toByte()) + version, 0x9000)
        }

        private fun tonPublicKey(seed: Int): ByteArray =
            PrivateKeyEd25519(ByteArray(32) { (it * 31 + seed).toByte() }).publicKey().key.toByteArray()

        private fun ethPrivateKey(seed: Int): TwPrivateKey = TwPrivateKey(ByteArray(32) { (it * 7 + seed + 1).toByte() })
    }

    private companion object {
        const val PRIMARY = 1
        const val SECONDARY = 2
        const val DASHBOARD = "BOLOS"
    }
}
