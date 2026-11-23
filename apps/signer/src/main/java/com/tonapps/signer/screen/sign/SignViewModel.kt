package com.tonapps.signer.screen.sign

import android.content.Context
import androidx.lifecycle.ViewModel
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.toBigInteger
import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.icu.Coins as IcuCoins
import com.tonapps.signer.core.repository.KeyRepository
import com.tonapps.signer.password.Password
import com.tonapps.signer.screen.sign.body.SendPayload
import com.tonapps.signer.screen.sign.body.SignAction
import com.tonapps.signer.screen.sign.body.SignBody
import com.tonapps.signer.screen.sign.body.SignBodyException
import com.tonapps.signer.screen.sign.body.SignBodyParser
import com.tonapps.signer.screen.sign.list.SignItem
import com.tonapps.signer.vault.SignerVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import org.ton.kotlin.crypto.PrivateKeyEd25519
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.MsgAddressInt
import org.ton.cell.Cell
import com.tonapps.security.vault.safeArea
import com.tonapps.uikit.list.ListCell
import java.math.BigDecimal

class SignViewModel(
    private val id: Long,
    private val unsignedBody: Cell,
    private val v: String,
    private val seqno: Int,
    private val network: TonNetwork,
    private val repository: KeyRepository,
    private val vault: SignerVault,
): ViewModel() {

    val keyEntity = repository.getKey(id).filterNotNull()

    private val signBody: SignBody? = parseSignBody()

    val signable: Boolean
        get() = signBody != null

    val actionsFlow = MutableStateFlow(createItems(signBody)).asStateFlow()

    fun sign(context: Context) = Password.authenticate(context).onStart {
        check(signable) { "Body was not fully recognized" }
    }.safeArea {
        vault.getPrivateKey(it, id)
    }.map {
        sign(it)
    }.flowOn(Dispatchers.IO).take(1)

    fun openEmulate() = keyEntity.map {
        val version = signBody?.version ?: SignBodyParser.requestedVersion(v) ?: WalletVersion.V4R2
        val contract = BaseWalletContract.create(it.publicKey, version.title, network.value)
        val cell = contract.createTransferMessageCell(
            address = contract.address,
            privateKey = EmptyPrivateKeyEd25519.invoke(),
            seqNo = seqno,
            unsignedBody = unsignedBody
        )
        cell.hex()
    }.flowOn(Dispatchers.IO).take(1)

    private fun sign(privateKey: PrivateKeyEd25519): ByteArray {
        return privateKey.signToByteArray(unsignedBody.hash().toByteArray())
    }

    private fun parseSignBody(): SignBody? {
        return try {
            SignBodyParser.parse(unsignedBody, v)
        } catch (_: SignBodyException) {
            null
        }
    }

    private fun createItems(body: SignBody?): List<SignItem> {
        if (body == null) {
            return listOf(SignItem.Unknown(ListCell.Position.SINGLE))
        }
        return body.actions.mapIndexed { index, action ->
            createItem(action, ListCell.getPosition(body.actions.size, index))
        }
    }

    private fun createItem(action: SignAction, position: ListCell.Position): SignItem {
        return when (action) {
            is SignAction.Send -> createSendItem(action, position)
            is SignAction.AddExtension -> SignItem.WalletAction(
                actionType = SignItem.WalletActionType.ADD_EXTENSION,
                address = parseAddress(action.address),
                value = null,
                position = position
            )
            is SignAction.RemoveExtension -> SignItem.WalletAction(
                actionType = SignItem.WalletActionType.REMOVE_EXTENSION,
                address = parseAddress(action.address),
                value = null,
                position = position
            )
            is SignAction.SetSignatureAuth -> SignItem.WalletAction(
                actionType = if (action.allowed) {
                    SignItem.WalletActionType.ENABLE_SIGNATURE_AUTH
                } else {
                    SignItem.WalletActionType.DISABLE_SIGNATURE_AUTH
                },
                address = null,
                value = null,
                position = position
            )
            is SignAction.InstallPlugin -> SignItem.WalletAction(
                actionType = SignItem.WalletActionType.INSTALL_PLUGIN,
                address = parseAddress(action.address),
                value = formatCoins(action.amount),
                position = position
            )
            is SignAction.RemovePlugin -> SignItem.WalletAction(
                actionType = SignItem.WalletActionType.REMOVE_PLUGIN,
                address = parseAddress(action.address),
                value = formatCoins(action.amount),
                position = position
            )
        }
    }

    private fun createSendItem(action: SignAction.Send, position: ListCell.Position): SignItem.Send {
        val payload = action.payload
        val target = when (payload) {
            is SendPayload.Jetton -> payload.recipient
            is SendPayload.Nft -> payload.newOwner
            SendPayload.Ton, is SendPayload.Unknown -> action.destination
        }
        val excess = when (payload) {
            is SendPayload.Jetton -> payload.excess
            is SendPayload.Nft -> payload.excess
            SendPayload.Ton, is SendPayload.Unknown -> null
        }
        return SignItem.Send(
            target = parseAddress(target, false),
            value = formatCoins(action.value),
            sendsEntireBalance = action.sendsEntireBalance,
            destroysContract = action.destroysContract,
            tokenAddress = (payload as? SendPayload.Jetton)?.let { parseAddress(action.destination) },
            nftAddress = (payload as? SendPayload.Nft)?.let { parseAddress(action.destination) },
            excessAddress = excess?.let { parseAddress(it) },
            unknownOpCode = (payload as? SendPayload.Unknown)?.let { "0x${it.opCode.toString(16)}" },
            comment = action.comment,
            position = position
        )
    }

    private fun formatCoins(coins: Coins): String {
        val value = BigDecimal(coins.amount.value.toBigInteger(), TON_DECIMALS)
        return CurrencyFormatter.formatFull("TON", IcuCoins.of(value, TON_DECIMALS), TON_DECIMALS).toString()
    }

    private fun parseAddress(address: MsgAddressInt, bounceable: Boolean = true): String {
        if (address is AddrStd) {
            return address.toString(userFriendly = true, bounceable = bounceable)
        }
        return "none"
    }
}

private const val TON_DECIMALS = 9
