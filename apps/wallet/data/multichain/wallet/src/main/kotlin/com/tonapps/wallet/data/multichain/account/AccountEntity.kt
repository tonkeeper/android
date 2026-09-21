package com.tonapps.wallet.data.multichain.account

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Derivation
import com.tonapps.chainkit.core.chain.model.account.PubKey
import com.tonapps.chainkit.core.chain.model.num.BaseUnit
import com.tonapps.chainkit.core.chain.model.num.DisplayUnit
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.extensions.generateUuid
import com.tonapps.extensions.lazyUnsafe
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.asset.AssetRateEntity

data class AccountsWithTotal(
    val accounts: List<AccountWithDetails>,
    val total: BigDecimal,
    val nextCursor: String? = null,
)

@Entity(
    tableName = "account",
    indices = [
        Index(
            value = ["wallet_id"],
            name = "idx_network_wallet_id",
        ),
    ]
)
data class AccountEntity(
    @ColumnInfo("wallet_id")
    val walletId: String,
    @ColumnInfo("network")
    val network: String,
    @ColumnInfo("mode")
    val mode: String,
    @ColumnInfo("display_address")
    val displayAddress: String,
    @ColumnInfo("public_key")
    val publicKey: String,
    @ColumnInfo("segwit_public_key")
    val segwitPublicKey: String,

    @ColumnInfo("address_type")
    val addressType: Address.Type = Address.Type.Default,

    @PrimaryKey
    @ColumnInfo("id")
    val id: String = generateUuid(),
) {
    val chain by lazyUnsafe {
        Chain.find(network, mode)!!
    }

    // Account with the chain-native asset (ETH, BTC, …) — for chain-level flows like WC session
    // approval. Token positions build their Account via AccountWithDetails.value instead.
    val chainAccount: Account by lazyUnsafe {
        Account(
            address = Address.force(displayAddress, chain, addressType),
            asset = chain.toAsset(),
            derivation = Derivation.DefaultPath,
            publicKey = PubKey(publicKey, segwitPublicKey),
        )
    }
}

data class AccountBalanceEntity(
    val available: String = "0"
)

data class AccountWithDetails(
    val data: AccountEntity,
    val balance: AccountBalanceEntity,
    val asset: AssetEntity,
    val rate: AssetRateEntity?,
    val isHidden: Boolean = false,
) {
    val unitBalance: BaseUnit by lazyUnsafe {
        asset.value.decimals.baseUnit(balance.available)
    }

    val displayBalance: DisplayUnit by lazyUnsafe {
        unitBalance.toDisplayUnit()
    }

    val formattedBalance: String by lazyUnsafe {
        Formatter.formatShort(value = displayBalance)
    }

    val fiatValue: BigDecimal by lazyUnsafe {
        if (rate != null) {
            displayBalance.value.multiply(rate.value.value)
        } else {
            BigDecimal.ZERO
        }
    }

    // Carries the position's specific asset (may be a token, e.g. USDC.ETH) — this Account builds
    // send/swap transactions, so it must not collapse to data.chainAccount's chain-native asset.
    val value: Account by lazyUnsafe {
        Account(
            address = Address.force(data.displayAddress, asset.value.chain, data.addressType),
            asset = asset.value,
            derivation = Derivation.DefaultPath,
            publicKey = PubKey(data.publicKey, data.segwitPublicKey),
        )
    }

    val address: Address by lazyUnsafe {
        Address.force(data.displayAddress, asset.value.chain, data.addressType)
    }
}
