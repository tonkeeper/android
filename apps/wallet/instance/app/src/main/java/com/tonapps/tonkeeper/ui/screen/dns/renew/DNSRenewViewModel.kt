package com.tonapps.tonkeeper.ui.screen.dns.renew

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.TONOpCode
import com.tonapps.blockchain.ton.extensions.storeOpCode
import com.tonapps.blockchain.ton.extensions.storeQueryId
import com.tonapps.extensions.currentTimeSeconds
import com.tonapps.icu.Coins
import com.tonapps.blockchain.model.legacy.TransferEntity
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.dns.renew.list.Item
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionScreen
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.collectibles.entities.DnsExpiringEntity
import com.tonapps.wallet.data.core.entity.RawMessageEntity
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ton.cell.CellBuilder

class DNSRenewViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val entities: List<DnsExpiringEntity>,
    private val collectiblesRepository: CollectiblesRepository,
    private val accountRepository: AccountRepository
) : BaseWalletVM(app) {

    private val dnsExpiringFlow = flow {
        emit(collectiblesRepository.getDnsExpiring(wallet.accountId, wallet.network, 366))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, entities)

    val uiItemsFlow = dnsExpiringFlow.map {
        val items = collectiblesRepository.getDnsExpiring(wallet.accountId, wallet.network, 366)
        val uiItems = items.mapIndexed { index, dnsExpiringEntity ->
            Item(
                position = ListCell.getPosition(items.size, index),
                wallet = wallet,
                entity = dnsExpiringEntity
            )
        }
        uiItems
    }

    val showRenewAllButtonFlow = dnsExpiringFlow.map {
        if (wallet.isWatchOnly || it.isEmpty()) {
            false
        } else {
            wallet.maxMessages >= it.size
        }
    }

    fun renewAll(successCallback: () -> Unit) {
        viewModelScope.launch {
            val items = dnsExpiringFlow.value.filter { !it.inSale }
            if (items.isEmpty()) {
                openScreen(DNSOnSaleScreen.newInstance())
            } else {
                val seqNo = accountRepository.getSeqno(wallet)
                val signRequests = createSignRequest(wallet, items, seqNo)
                if (sign(signRequests)) {
                    successCallback()
                }
            }
        }
    }

    private suspend fun sign(signRequests: List<SignRequestEntity>): Boolean {
        for (signRequest in signRequests) {
            try {
                SendTransactionScreen.run(context, wallet, signRequest)
            } catch (e: Throwable) {
                return false
            }
        }
        return true
    }

    companion object {

        fun createSignRequest(
            wallet: WalletEntity,
            entities: List<DnsExpiringEntity>,
            seqNo: Int
        ): List<SignRequestEntity> {
            val messagesChunks = entities.mapNotNull(::createMessage).chunked(wallet.maxMessages)
            val requests = mutableListOf<SignRequestEntity>()
            for ((index, messages) in messagesChunks.withIndex()) {
                val request = SignRequestEntity.Builder()
                    .setValidUntil(currentTimeSeconds() + 10 * 60)
                    .setTestnet(wallet.testnet)
                    .setFrom(wallet.contract.address)
                    .addMessages(messages)
                    .setSeqNo(seqNo + index)
                    .build(Uri.EMPTY)
                requests.add(request)
            }

            return requests
        }

        private fun createMessage(entity: DnsExpiringEntity): RawMessageEntity? {
            val dns = entity.dnsItem ?: return null
            return createMessage(dns.address)
        }

        fun createMessage(address: String): RawMessageEntity {
            val payload = CellBuilder.createCell {
                storeOpCode(TONOpCode.CHANGE_DNS_RECORD)
                storeQueryId(TransferEntity.newWalletQueryId())
                storeUInt(0, 256)
            }

            return RawMessageEntity.of(
                amount = Coins.of("0.02").toBigInteger(),
                address = address,
                payload = payload,
            )
        }
    }

}