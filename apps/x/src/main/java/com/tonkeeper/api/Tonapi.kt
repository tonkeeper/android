package com.tonkeeper.api

import com.tonkeeper.api.base.BaseAPI
import com.tonkeeper.api.base.SourceAPI
import io.tonapi.models.Account
import io.tonapi.models.EmulateMessageToWalletRequest
import io.tonapi.models.MessageConsequences
import io.tonapi.models.SendBlockchainMessageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.cell.Cell
import ton.extensions.base64

object Tonapi {
    private val main = BaseAPI("https://keeper.tonapi.io")
    private val test = BaseAPI("https://testnet.tonapi.io")

    val accounts = SourceAPI(main.accounts, test.accounts)

    val blockchain = SourceAPI(main.blockchain, test.blockchain)

    val connect = SourceAPI(main.connect, test.connect)

    val dns = SourceAPI(main.dns, test.dns)

    val emulation = SourceAPI(main.emulation, test.emulation)

    val events = SourceAPI(main.events, test.events)

    val jettons = SourceAPI(main.jettons, test.jettons)

    val liteServer = SourceAPI(main.liteServer, test.liteServer)

    val nft = SourceAPI(main.nft, test.nft)

    val rates = SourceAPI(main.rates, test.rates)

    val staking = SourceAPI(main.staking, test.staking)

    val storage = SourceAPI(main.storage, test.storage)

    val traces = SourceAPI(main.traces, test.traces)

    val wallet = SourceAPI(main.wallet, test.wallet)

    suspend fun resolveAccount(
        value: String,
        testnet: Boolean
    ): Account? {
        if (testnet) {
            return test.resolveAccount(value)
        }
        return main.resolveAccount(value)
    }

    suspend fun getAccountSeqno(
        accountId: String,
        testnet: Boolean,
    ): Int = withContext(Dispatchers.IO) {
        wallet.get(testnet).getAccountSeqno(accountId).seqno
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

    suspend fun emulate(
        boc: String,
        testnet: Boolean
    ): MessageConsequences = withContext(Dispatchers.IO) {
        val request = EmulateMessageToWalletRequest(boc)
        emulation.get(testnet).emulateMessageToWallet(request)
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
            blockchain.get(testnet).sendBlockchainMessage(request)
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