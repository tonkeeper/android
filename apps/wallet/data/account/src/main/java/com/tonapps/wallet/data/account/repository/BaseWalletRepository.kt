package com.tonapps.wallet.data.account.repository

import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.Extras
import com.tonapps.wallet.data.account.WalletSource
import com.tonapps.wallet.data.account.entities.MessageBodyEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.entities.WalletEvent
import com.tonapps.wallet.data.account.entities.WalletLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer

abstract class BaseWalletRepository(
    private val scope: CoroutineScope,
    private val api: API
) {

    val _walletsFlow = MutableStateFlow<List<WalletEntity>?>(null)
    val walletsFlow = _walletsFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    val _activeWalletFlow = MutableStateFlow<WalletEntity?>(null)
    val activeWalletFlow = _activeWalletFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    @OptIn(ExperimentalCoroutinesApi::class)
    val realtimeEventsFlow = activeWalletFlow.flatMapLatest { wallet ->
        api.accountEvents(wallet.accountId, wallet.testnet).map { event ->
            val isBoc = event.json.has("boc")
            if (isBoc) {
                WalletEvent.Boc(wallet, event.json)
            } else {
                WalletEvent.Transaction(wallet, event.json)
            }
        }
    }.flowOn(Dispatchers.IO).shareIn(scope, SharingStarted.Eagerly, 1)

    abstract suspend fun getWallets(): List<WalletEntity>

    abstract suspend fun getActiveWalletId(): String

    abstract fun removeCurrent()

    abstract suspend fun getTonProofToken(walletId: String): String?

    abstract suspend fun getWalletByAccountId(accountId: String): WalletEntity?

    abstract suspend fun getWalletById(id: String): WalletEntity?

    fun chooseWallet(id: String) {
        scope.launch {
            setActiveWallet(id)
        }
    }

    abstract suspend fun setActiveWallet(id: String): WalletEntity?

    abstract suspend fun editLabel(
        id: String,
        name: String,
        emoji: String,
        color: Int
    )

    fun saveLabel(name: String, emoji: String, color: Int) {
        scope.launch(Dispatchers.IO) {
            editLabel(name, emoji, color)
        }
    }

    abstract suspend fun editLabel(
        name: String,
        emoji: String,
        color: Int
    )

    abstract suspend fun createNewWallet(label: WalletLabel): WalletEntity

    abstract suspend fun addWatchWallet(
        publicKey: PublicKeyEd25519,
        label: WalletLabel,
        version: WalletVersion,
        source: WalletSource
    ): WalletEntity

    abstract suspend fun addWallets(
        mnemonic: List<String>,
        publicKey: PublicKeyEd25519,
        versions: List<WalletVersion>,
        name: String? = null,
        emoji: CharSequence,
        color: Int,
        testnet: Boolean
    ): List<WalletEntity>

    abstract suspend fun addSignerWallet(
        publicKey: PublicKeyEd25519,
        name: String,
        emoji: CharSequence,
        color: Int,
        source: WalletSource,
        versions: List<WalletVersion>
    ): List<WalletEntity>

    abstract suspend fun getMnemonic(id: String): Array<String>

    abstract suspend fun getPrivateKey(id: String): PrivateKeyEd25519

    suspend fun getSeqno(
        wallet: WalletEntity
    ): Int = withContext(Dispatchers.IO) {
        try {
            api.getAccountSeqno(wallet.accountId, wallet.testnet)
        } catch (e: Throwable) {
            0
        }
    }

    suspend fun getValidUntil(testnet: Boolean): Long {
        val seconds = api.getServerTime(testnet)
        return seconds + (5 * 30L) // 5 minutes
    }

    fun messageBody(
        wallet: WalletEntity,
        seqno: Int,
        validUntil: Long,
        transfers: List<WalletTransfer>
    ): MessageBodyEntity {
        val body = wallet.createBody(seqno, validUntil, transfers)
        return MessageBodyEntity(seqno, body, validUntil)
    }

    abstract suspend fun clear()

    fun createSignedMessage(
        wallet: WalletEntity,
        seqno: Int,
        privateKeyEd25519: PrivateKeyEd25519,
        validUntil: Long,
        transfers: List<WalletTransfer>
    ): Cell {
        val data = messageBody(wallet, seqno, validUntil, transfers)
        return wallet.sign(privateKeyEd25519, data.seqno, data.body)
    }
}