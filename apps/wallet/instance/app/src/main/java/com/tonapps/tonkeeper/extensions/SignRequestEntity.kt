package com.tonapps.tonkeeper.extensions

import com.tonapps.blockchain.ton.TonSendMode
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.wallet.api.API
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.block.AddrStd
import org.ton.contract.wallet.WalletTransfer

suspend fun SignRequestEntity.getTransfers(
    wallet: WalletEntity,
    compressedTokens: List<AccountTokenEntity>,
    excessesAddress: AddrStd? = null,
    api: API,
    batteryEnabled: Boolean,
    tonBalance: com.tonapps.icu.Coins? = null,
): List<WalletTransfer> = withContext(Dispatchers.IO) {
    val transferMessages = getTransferMessages(batteryEnabled)
    val transfers = mutableListOf<WalletTransfer>()
    for (message in transferMessages) {
        val sendMode = if (tonBalance != null && message.amount == tonBalance.toBigInteger()) {
            TonSendMode.CARRY_ALL_REMAINING_BALANCE.value + TonSendMode.IGNORE_ERRORS.value
        } else {
            TonSendMode.PAY_GAS_SEPARATELY.value + TonSendMode.IGNORE_ERRORS.value
        }
        if (message.withBattery && batteryEnabled) {
            transfers.add(message.getDefaultWalletTransfer(sendMode))
        } else {
            val jetton = compressedTokens.firstOrNull {
                it.address.equalsAddress(message.addressValue) ||
                        it.balance.walletAddress.equalsAddress(message.addressValue)
            }
            val jettonCustomPayload = jetton?.let {
                api.getJettonCustomPayload(wallet.accountId, wallet.network, it.address)
            }

            val transfer = message.getWalletTransfer(
                excessesAddress = excessesAddress,
                newStateInit = jettonCustomPayload?.stateInit,
                newCustomPayload = jettonCustomPayload?.customPayload,
                sendMode = sendMode,
            )
            transfers.add(transfer)
        }
    }
    transfers
}