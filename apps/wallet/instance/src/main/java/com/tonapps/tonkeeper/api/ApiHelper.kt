package com.tonapps.tonkeeper.api

import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.wallet.api.Tonapi
import io.tonapi.models.Account
import io.tonapi.models.EmulateMessageToWalletRequest
import io.tonapi.models.MessageConsequences
import io.tonapi.models.SendBlockchainMessageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.cell.Cell
import ton.TonAddress

object ApiHelper {

    suspend fun getAccountSeqno(
        accountId: String,
        testnet: Boolean,
    ): Int = withContext(Dispatchers.IO) {
        Tonapi.wallet.get(testnet).getAccountSeqno(accountId).seqno
    }

    suspend fun getAccountSeqnoOrZero(
        accountId: String,
        testnet: Boolean,
    ): Int {
        return try {
            getAccountSeqno(accountId, testnet)
        } catch (e: Throwable) {
            0
        }
    }

    suspend fun resolveAccount(
        value: String,
        testnet: Boolean,
    ): Account? = withContext(Dispatchers.IO) {
        try {
            if (!TonAddress.isValid(value)) {
                return@withContext resolveDomain(value.lowercase().trim(), testnet)
            }
            return@withContext getAccount(value, testnet)
        } catch (ignored: Throwable) {}
        return@withContext null
    }

    private fun resolveDomain(
        domain: String,
        testnet: Boolean,
        suffixList: Array<String> = arrayOf(".ton", ".t.me")
    ): Account? {
        val accountId = domain.lowercase()
        var account: Account? = null
        try {
            account = getAccount(accountId, testnet)
        } catch (ignored: Throwable) {}

        for (suffix in suffixList) {
            if (account == null && !accountId.endsWith(suffix)) {
                try {
                    account = getAccount("$accountId$suffix", testnet)
                } catch (ignored: Throwable) {}
            }
        }
        if (account?.name == null) {
            account = account?.copy(name = accountId)
        }
        return account
    }

    private fun getAccount(accountId: String, testnet: Boolean): Account {
        return Tonapi.accounts.get(testnet).getAccount(accountId)
    }

    suspend fun emulate(
        boc: String,
        testnet: Boolean
    ): MessageConsequences = withContext(Dispatchers.IO) {
        val request = EmulateMessageToWalletRequest(boc)
        Tonapi.emulation.get(testnet).emulateMessageToWallet(request)
    }

    suspend fun emulate(
        cell: Cell,
        testnet: Boolean
    ): MessageConsequences {
        return emulate(cell.base64(), testnet)
    }

    suspend fun sendToBlockchain(
        boc: String,
        testnet: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = SendBlockchainMessageRequest(boc)
            Tonapi.blockchain.get(testnet).sendBlockchainMessage(request)
            true
        } catch (e: Throwable) {
            false
        }
    }

    suspend fun sendToBlockchain(
        cell: Cell,
        testnet: Boolean
    ) = sendToBlockchain(cell.base64(), testnet)

}