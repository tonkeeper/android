package com.tonapps.wallet.data.multichain.vault

import com.tonapps.chainkit.core.chain.model.account.WalletKeyPair
import com.tonapps.chainkit.core.chain.model.account.WalletKind
import com.trustwallet.core.WalletCoreLibLoader
import org.junit.Assert.assertEquals
import org.junit.Test

class WalletKeyRepositoryTest {

    init {
        WalletCoreLibLoader.loadLibrary()
    }

    @Test
    fun `a ledger wallet id gives the ledger key pair`() {
        val keyPair = WalletKeyRepository.keyPair(LEDGER_WALLET_ID, LEDGER_AUTH_KEY.hexToBytes())

        assertEquals(WalletKind.Ledger, keyPair.kind)
        assertEquals(LEDGER_WALLET_ID, keyPair.walletIdV2())
    }

    @Test
    fun `a multichain wallet id gives the multichain key pair`() {
        val walletId = WalletKeyPair.fromPrivateKey(LEDGER_AUTH_KEY.hexToBytes(), WalletKind.Multichain).walletIdV2()

        val keyPair = WalletKeyRepository.keyPair(walletId, LEDGER_AUTH_KEY.hexToBytes())

        assertEquals(WalletKind.Multichain, keyPair.kind)
        assertEquals(walletId, keyPair.walletIdV2())
    }

    @Test
    fun `a wallet id of the older format keeps the multichain key pair`() {
        val keyPair = WalletKeyRepository.keyPair(OLDER_WALLET_ID, LEDGER_AUTH_KEY.hexToBytes())

        assertEquals(WalletKind.Multichain, keyPair.kind)
    }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        const val LEDGER_AUTH_KEY = "c878480d4f91bdad08453e5985c3485e2de2d04c60fa96d1e793b7e61a58132d"
        const val LEDGER_WALLET_ID = "akabnyroxo54kwlbwhm2762krvdqgscfcreq"
        const val OLDER_WALLET_ID = "6humw3uhgiu77f4jlicetrwm2ql6tnq3"
    }
}
