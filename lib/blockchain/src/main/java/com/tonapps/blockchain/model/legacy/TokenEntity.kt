package com.tonapps.blockchain.model.legacy

import android.net.Uri
import android.os.Parcelable
import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.blockchain.contract.TokenType
import com.tonapps.blockchain.model.legacy.WalletCurrency.Chain
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.icu.Coins
import com.tonapps.lib.blockchain.R
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.tlb.CellRef
import java.math.BigDecimal

@Serializable
@Parcelize
data class TokenEntity(
    @Serializable(with = BlockchainEnumSerializer::class)
    val blockchain: Blockchain,
    val address: String,
    val name: String,
    val symbol: String,
    @Serializable(with = UriSerializer::class)
    val imageUri: Uri,
    val decimals: Int,
    val verification: Verification,
    val isRequestMinting: Boolean,
    val isTransferable: Boolean,
    val lock: Lock? = null,
    val customPayloadApiUri: String?,
    @Serializable(with = BigDecimalSerializer::class)
    val numerator: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val denominator: BigDecimal? = null,
): Parcelable {

    companion object {
        val TON_ICON_URI = Uri.Builder().scheme("res").path(R.drawable.ic_ton_with_bg.toString()).build()
        val USDT_ICON_URI = Uri.Builder().scheme("res").path(R.drawable.ic_usdt_with_bg.toString()).build()
        val USDE_ICON_URI = Uri.Builder().scheme("res").path(R.drawable.ic_udse_ethena_with_bg.toString()).build()
        val TS_USDE_ICON_URI = Uri.Builder().scheme("res").path(R.drawable.ic_tsusde_with_bg.toString()).build()
        val TRX_ICON_URI = Uri.Builder().scheme("res").path(R.drawable.ic_trx.toString()).build()

        const val TRC20_USDT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        const val TON_USDT = "0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe"
        const val TON_USDE = "0:086fa2a675f74347b08dd4606a549b8fdb98829cb282bc1949d3b12fbaed9dcc"

        const val TON_TS_USDE = "0:d0e545323c7acb7102653c073377f7e3c67f122eb94d430a250739f109d4a57d"

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
            symbol = "USDT",
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
            symbol = "USDT",
            imageUri = USDT_ICON_URI,
            decimals = 6,
            verification = Verification.whitelist,
            isRequestMinting = false,
            isTransferable = true,
            customPayloadApiUri = null
        )

        val TRX = TokenEntity(
            blockchain = Blockchain.TRON,
            address = "TRX",
            name = "Tron TRX",
            symbol = "TRX",
            imageUri = TRX_ICON_URI,
            decimals = 6,
            verification = Verification.whitelist,
            isRequestMinting = false,
            isTransferable = true,
            customPayloadApiUri = null
        )

        val USDE = TokenEntity(
            blockchain = Blockchain.TON,
            address = TON_USDE,
            name = "Ethena USDe",
            symbol = "USDe",
            imageUri = USDE_ICON_URI,
            decimals = 6,
            verification = Verification.whitelist,
            isRequestMinting = false,
            isTransferable = true,
            customPayloadApiUri = null
        )

        val TS_USDE = TokenEntity(
            blockchain = Blockchain.TON,
            address = TON_TS_USDE,
            name = "Ethena tsUSDe",
            symbol = "tsUSDe",
            imageUri = TS_USDE_ICON_URI,
            decimals = 6,
            verification = Verification.whitelist,
            isRequestMinting = false,
            isTransferable = true,
            customPayloadApiUri = null
        )
    }

    val isTsTON: Boolean
        get() = verification == Verification.whitelist && symbol.equals("tsTON", true)

    val isTsUSDe: Boolean
        get() = verification == Verification.whitelist && symbol.equals("tsUSDe", true)

    val isLiquid: Boolean
        get() = isTsTON || isTsUSDe

    @Serializable
    enum class Verification {
        whitelist, blacklist, none
    }

    @Serializable
    @Parcelize
    data class Lock(
        val amount: String,
        val till: Long
    ): Parcelable {

    }

    data class TransferPayload(
        val tokenAddress: String,
        val customPayload: Cell? = null,
        val stateInit: CellRef<StateInit>? = null
    ) {

        val isEmpty: Boolean
            get() = customPayload == null && stateInit == null

        companion object {
            fun empty(tokenAddress: String): TransferPayload {
                return TransferPayload(tokenAddress)
            }
        }
    }

    enum class Extension(val value: String) {
        NonTransferable("non_transferable"),
        CustomPayload("custom_payload")
    }

    @IgnoredOnParcel
    val asCurrency by lazy {
        WalletCurrency(
            code = symbol,
            title = name,
            chain = if (blockchain == Blockchain.TRON) Chain.TRON(address, decimals) else Chain.TON(address, decimals),
            iconUrl = imageUri.toString(),
            isToken = tokenType != null,
            isFiat = false,
        )
    }

    @IgnoredOnParcel
    val tokenType by lazy {
        if (!isUsdt && !isUsdtTrc20) {
            return@lazy null
        }

        when (blockchain) {
            Blockchain.TON -> TokenType.Defined.JETTON
            Blockchain.TRON -> TokenType.Defined.TRC20
        }
    }

    val isTon: Boolean
        get() = address == TON.address

    @IgnoredOnParcel
    val isUsdt: Boolean by lazy {
        address.equalsAddress(TON_USDT)
    }

    @IgnoredOnParcel
    val isUsdtTrc20: Boolean by lazy {
        address.equalsAddress(TRC20_USDT)
    }

    @IgnoredOnParcel
    val isTrx: Boolean by lazy {
        address == TRX.address
    }

    val plainSymbol: String by lazy { // TODO remove after deposit merge
        return@lazy symbol.replace("₮", "T")
    }

    val verified: Boolean
        get() = verification == Verification.whitelist

    val blacklist: Boolean
        get() = verification == Verification.blacklist

    fun toUIAmount(amount: Coins): Coins {
        if (numerator == null || denominator == null) {
            return amount
        }

        return Coins.Companion.of(
            amount.value * numerator / denominator,
            decimals
        )
    }
}