package com.tonapps.wallet.data.ledger.lab

import com.tonapps.blockchain.ton.contract.WalletV4R2Contract
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.wallet.data.ledger.LedgerChainApp
import com.tonapps.wallet.data.ledger.discovery.LedgerAppDiscoveryState
import com.tonapps.wallet.data.ledger.discovery.LedgerDevice
import com.tonapps.wallet.data.ledger.discovery.LedgerDiscoveryInteractorImpl
import com.trustwallet.core.AnyAddress
import com.trustwallet.core.CoinType
import com.trustwallet.core.HDWallet
import com.trustwallet.core.WalletCoreLibLoader
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.ton.kotlin.crypto.PublicKeyEd25519

class LedgerLabDiscoveryTest {

    private lateinit var wallet: HDWallet

    @Before
    fun setUp() {
        assumeTrue("Ledger lab is not running", SpeculosLab.isRunning)
        WalletCoreLibLoader.loadLibrary()
        wallet = HDWallet(SpeculosLab.seed, "")
        SpeculosLab.policy("approve")
        SpeculosLab.set("current" to kotlinx.serialization.json.JsonPrimitive("BOLOS"))
    }

    @Test
    fun `discovery reads the same addresses a software wallet derives from the seed`() = runBlocking {
        val controller = SpeculosLab.controller()
        val result = LedgerDiscoveryInteractorImpl(controller).discover(
            device = LedgerDevice("lab", "Stax lab", LedgerModel.Stax),
            indices = listOf(0, 1),
            apps = LedgerChainApp.entries.toList(),
        )
        assertEquals(LedgerChainApp.entries.associateWith { LedgerAppDiscoveryState.Done }, result.apps)
        for (account in result.accounts) {
            val index = account.index
            val byChain = account.accounts.associateBy { it.chain }

            val tonKey = requireNotNull(wallet.getKey(CoinType.TON, "m/44'/607'/0'/0'/$index'/0'")).getPublicKeyEd25519().data
            val tonAddress = WalletV4R2Contract(publicKey = PublicKeyEd25519(ByteString(tonKey))).address
            assertEquals(tonAddress.toString(userFriendly = true, bounceable = false), byChain.getValue(Chain.Ton.Mainnet).displayAddress)

            val evmKey = requireNotNull(wallet.getKey(CoinType.Ethereum, "m/44'/60'/$index'/0/0"))
            val evmAddress = AnyAddress(evmKey.getPublicKeySecp256k1(false), CoinType.Ethereum).description
            val evmDrafts = account.accounts.filter { it.chain.isEvm }
            check(evmDrafts.isNotEmpty())
            evmDrafts.forEach { assertEquals(evmAddress.lowercase(), it.displayAddress.lowercase()) }

            val tronKey = requireNotNull(wallet.getKey(CoinType.Tron, "m/44'/195'/$index'/0/0"))
            assertEquals(AnyAddress(tronKey.getPublicKeySecp256k1(false), CoinType.Tron).description, byChain.getValue(Chain.Tron.Mainnet).displayAddress)

            val btcKey = requireNotNull(wallet.getKey(CoinType.Bitcoin, "m/84'/0'/$index'/0/0"))
            assertEquals(AnyAddress(btcKey.getPublicKeySecp256k1(true), CoinType.Bitcoin).description, byChain.getValue(Chain.Bitcoin.Mainnet).displayAddress)

            val solanaKey = requireNotNull(wallet.getKey(CoinType.Solana, "m/44'/501'/$index'/0'"))
            assertEquals(AnyAddress(solanaKey.getPublicKeyEd25519(), CoinType.Solana).description, byChain.getValue(Chain.Solana.Mainnet).displayAddress)
        }
    }
}
