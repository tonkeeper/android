package com.tonapps.wallet.testing

import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.wallet.data.multichain.account.AccountBalanceEntity
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.asset.AssetRateEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletType

/**
 * One wallet every test can share, so recorded backend responses and the accounts they were quoted
 * for stay in agreement. Its addresses come from `chainkit --json wallet ls` for the CLI's stored
 * `swap` wallet, which means chainkit validates them for real.
 */
object TestWallet {

    const val ID = "aia4kzm6zmtrdhv3iqy3knmnjaxoqy54migq"

    const val TON_ADDRESS = "UQC1zHXDFDKegQmkRuPjT9G6mZ-9pDVp89zUWrXA3WfyqMJm"
    const val ETH_ADDRESS = "0x0eE3CdEE4f010BF6c49aF86E9e67ef2bc80a508b"
    const val BTC_ADDRESS = "bc1qnmmy8w4kf2ews60wzyyafnxuvyteesmypqtpsn"
    const val TRON_ADDRESS = "TRfhcmuwsUmGBsJDQGoxJHHy1s4z6ti8yM"

    const val TON_PUBLIC_KEY = "0x66c63e4ab095d55a21d148972b691efcc16d6e757b28aa52c9ac56b54ccefa4d"

    /** The address this wallet holds on the asset's chain. */
    fun addressOf(asset: AssetEntity): String = when (asset.value.chain.network.type.id) {
        "ton" -> TON_ADDRESS
        "btc" -> BTC_ADDRESS
        "tron" -> TRON_ADDRESS
        else -> ETH_ADDRESS
    }

    /** The address flavour the wallet uses on that chain. */
    fun addressTypeOf(asset: AssetEntity): Address.Type = when (asset.value.chain.network.type.id) {
        "ton" -> Address.Type.TonV5R1
        "btc" -> Address.Type.BtcSegwit
        else -> Address.Type.Default
    }
}

fun testWallet(
    id: String = TestWallet.ID,
    name: String = "Test",
    type: McWalletType = McWalletType.Multicoin,
) = McWalletEntity(
    credentialId = "test-credential",
    name = name,
    id = id,
    type = type,
)

fun testAsset(
    id: String,
    symbol: String,
    name: String = symbol,
    decimals: Int,
    imageUrl: String = "",
) = AssetEntity(
    id = id,
    name = name,
    symbol = symbol,
    decimals = decimals,
    imageUrl = imageUrl,
)

/** Common assets, spelled the way the backend spells them. */
object TestAssets {
    val ton = testAsset("ton/mainnet/coin", "TON", "Toncoin", decimals = 9)
    val usdtTon = testAsset(
        id = "ton/mainnet/jetton/0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe",
        symbol = "USDT",
        name = "Tether USD",
        decimals = 6,
    )
    val eth = testAsset("eth/mainnet/coin", "ETH", "Ethereum", decimals = 18)
    val usdtEth = testAsset(
        id = "eth/mainnet/erc20/0xdAC17F958D2ee523a2206206994597C13D831ec7",
        symbol = "USDT",
        name = "Tether USD",
        decimals = 6,
    )
    val btc = testAsset("btc/mainnet/coin", "BTC", "Bitcoin", decimals = 8)
    val trx = testAsset("tron/mainnet/coin", "TRX", "Tron", decimals = 6)
    val usdtTron = testAsset(
        id = "tron/mainnet/trc20/TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
        symbol = "USDT",
        name = "Tether USD",
        decimals = 6,
    )
}

/**
 * An account of [TestWallet] on the asset's chain. [balance] is in base units, [price] is the fiat
 * rate; pass `price = null` for an asset the wallet has no rate for.
 */
fun testAccount(
    asset: AssetEntity,
    balance: String = "0",
    price: String? = "1.0",
    walletId: String = TestWallet.ID,
    address: String = TestWallet.addressOf(asset),
    addressType: Address.Type = TestWallet.addressTypeOf(asset),
): AccountWithDetails {
    val chain = asset.value.chain
    return AccountWithDetails(
        data = AccountEntity(
            walletId = walletId,
            network = chain.network.type.id,
            mode = chain.network.mode.id,
            displayAddress = address,
            publicKey = TestWallet.TON_PUBLIC_KEY,
            segwitPublicKey = "",
            addressType = addressType,
        ),
        balance = AccountBalanceEntity(available = balance),
        asset = asset,
        rate = price?.let {
            AssetRateEntity(price = it, percentChange24h = "0", currencyCode = "USD")
        },
    )
}
