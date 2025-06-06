package com.tonapps.wallet.api.entity

import android.net.Uri
import android.os.Parcelable
import com.tonapps.blockchain.ton.extensions.cellFromHex
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.wallet.api.R
import io.tonapi.models.JettonBalanceLock
import io.tonapi.models.JettonInfo
import io.tonapi.models.JettonPreview
import io.tonapi.models.JettonTransferPayload
import io.tonapi.models.JettonVerificationType
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.tlb.CellRef
import org.ton.tlb.asRef

@Parcelize
data class TokenEntity(
    val blockchain: Blockchain,
    val address: String,
    val name: String,
    val symbol: String,
    val imageUri: Uri,
    val decimals: Int,
    val verification: Verification,
    val isRequestMinting: Boolean,
    val isTransferable: Boolean,
    val lock: Lock? = null,
    val customPayloadApiUri: String?
): Parcelable {

    val isTsTON: Boolean
        get() = verification == Verification.whitelist && symbol.equals("tsTON", true)

    val isTsUSDe: Boolean
        get() = verification == Verification.whitelist && symbol.equals("tsUSDe", true)

    val isLiquid: Boolean
        get() = isTsTON || isTsUSDe

    enum class Verification {
        whitelist, blacklist, none
    }

    @Parcelize
    data class Lock(
        val amount: String,
        val till: Long
    ): Parcelable {

        constructor(lock: JettonBalanceLock) : this(
            amount = lock.amount,
            till = lock.till
        )
    }

    data class TransferPayload(
        val tokenAddress: String,
        val customPayload: Cell? = null,
        val stateInit: CellRef<StateInit>? = null
    ) {

        companion object {

            fun empty(tokenAddress: String): TransferPayload {
                return TransferPayload(tokenAddress)
            }
        }

        val isEmpty: Boolean
            get() = customPayload == null && stateInit == null

        constructor(tokenAddress: String, model: JettonTransferPayload) : this(
            tokenAddress = tokenAddress,
            customPayload = model.customPayload?.cellFromHex(),
            stateInit = model.stateInit?.cellFromHex()?.asRef(StateInit),
        )
    }

    enum class Extension(val value: String) {
        NonTransferable("non_transferable"),
        CustomPayload("custom_payload")
    }

    companion object {

        val TON_ICON_URI = Uri.Builder().scheme("res").path(R.drawable.ic_ton_with_bg.toString()).build()
        val USDT_ICON_URI = Uri.Builder().scheme("res").path(R.drawable.ic_usdt_with_bg.toString()).build()

        const val TRC20_USDT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        const val TON_USDT = "0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe"

        val TON = TokenEntity(
            blockchain = Blockchain.TON,
            address = "TON",
            name = "Toncoin",
            symbol = "TON",
            imageUri = TON_ICON_URI,
            decimals = 9,
            verification = Verification.whitelist,
            isRequestMinting = false,
            isTransferable = true,
            customPayloadApiUri = null
        )

        val USDT = TokenEntity(
            blockchain = Blockchain.TON,
            address = TON_USDT,
            name = "Tether",
            symbol = "USD₮",
            imageUri = USDT_ICON_URI,
            decimals = 6,
            verification = Verification.whitelist,
            isRequestMinting = false,
            isTransferable = true,
            customPayloadApiUri = null
        )

        val TRON_USDT = TokenEntity(
            blockchain = Blockchain.TRON,
            address = TRC20_USDT,
            name = "Tether",
            symbol = "USD₮",
            imageUri = USDT_ICON_URI,
            decimals = 6,
            verification = Verification.whitelist,
            isRequestMinting = false,
            isTransferable = true,
            customPayloadApiUri = null
        )

        private fun convertVerification(verification: JettonVerificationType): Verification {
            return when (verification) {
                JettonVerificationType.whitelist -> Verification.whitelist
                JettonVerificationType.blacklist -> Verification.blacklist
                else -> Verification.none
            }
        }
    }

    val isTon: Boolean
        get() = address == TON.address

    @IgnoredOnParcel
    val isUsdt: Boolean by lazy {
        address.equalsAddress(TON_USDT)
    }

    @IgnoredOnParcel
    val isTrc20: Boolean by lazy {
        address == TRC20_USDT
    }

    val verified: Boolean
        get() = verification == Verification.whitelist

    val blacklist: Boolean
        get() = verification == TokenEntity.Verification.blacklist

    constructor(
        jetton: JettonPreview,
        extensions: List<String>? = null,
        lock: JettonBalanceLock? = null
    ) : this(
        blockchain = Blockchain.TON,
        address = jetton.address.toRawAddress(),
        name = jetton.name,
        symbol = jetton.symbol,
        imageUri = Uri.parse(jetton.image),
        decimals = jetton.decimals,
        verification = convertVerification(jetton.verification),
        isRequestMinting = extensions?.contains(Extension.CustomPayload.value) == true,
        isTransferable = extensions?.contains(Extension.NonTransferable.value) != true,
        lock = lock?.let { Lock(it) },
        customPayloadApiUri = jetton.customPayloadApiUri
    )

    constructor(
        jetton: JettonInfo,
        extensions: List<String>? = null,
        lock: JettonBalanceLock? = null
    ) : this(
        blockchain = Blockchain.TON,
        address = jetton.metadata.address.toRawAddress(),
        name = jetton.metadata.name,
        symbol = jetton.metadata.symbol,
        imageUri = Uri.parse(jetton.preview ?: jetton.metadata.image),
        decimals = jetton.metadata.decimals.toInt(),
        verification = convertVerification(jetton.verification),
        isRequestMinting = extensions?.contains(Extension.CustomPayload.value) == true,
        isTransferable = extensions?.contains(Extension.NonTransferable.value) != true,
        lock = lock?.let { Lock(it) },
        customPayloadApiUri = jetton.customPayloadApiUri
    )
}