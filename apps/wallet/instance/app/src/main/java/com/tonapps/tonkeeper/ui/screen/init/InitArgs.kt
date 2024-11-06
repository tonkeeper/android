package com.tonapps.tonkeeper.ui.screen.init

import android.os.Bundle
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.publicKeyFromBase64
import com.tonapps.blockchain.ton.extensions.publicKeyFromHex
import com.tonapps.extensions.getEnum
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.extensions.putEnum
import com.tonapps.ledger.ton.LedgerConnectData
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import com.tonapps.wallet.data.account.entities.WalletEntity
import org.ton.api.pub.PublicKeyEd25519
import uikit.base.BaseArgs

data class InitArgs(
    val type: Type,
    val name: String?,
    val publicKey: PublicKeyEd25519?,
    val ledgerConnectData: LedgerConnectData?,
    val accounts: List<AccountItem>?,
    val keystone: WalletEntity.Keystone?
) : BaseArgs() {

    enum class Type {
        New, Import, Watch, Testnet, Signer, SignerQR, Ledger, Keystone,
    }

    private companion object {
        private const val ARG_TYPE = "type"
        private const val ARG_PUBLIC_KEY = "pk"
        private const val ARG_NAME = "name"
        private const val ARG_WALLET_SOURCE = "wallet_source"
        private const val ARG_LEDGER_CONNECT_DATA = "ledger_connect_data"
        private const val ARG_ACCOUNTS = "accounts"
        private const val ARG_KEYSTONE = "keystone"
    }

    private val ledgerAccountName: String?
        get() = accounts?.firstOrNull {
            !it.name.isNullOrBlank()
        }?.name

    private val ledgerName: String?
        get() = ledgerConnectData?.model?.productName ?: ledgerAccountName

    val labelName: String?
        get() = name ?: ledgerName


    constructor(bundle: Bundle) : this(
        type = bundle.getEnum(ARG_TYPE, Type.New),
        name = bundle.getString(ARG_NAME),
        publicKey = bundle.getString(ARG_PUBLIC_KEY)?.publicKeyFromBase64(),
        accounts = bundle.getParcelableArrayList<AccountItem>(ARG_ACCOUNTS),
        ledgerConnectData = bundle.getParcelableCompat<LedgerConnectData>(ARG_LEDGER_CONNECT_DATA),
        keystone = bundle.getParcelableCompat(ARG_KEYSTONE)
    )

    override fun toBundle(): Bundle = Bundle().apply {
        putEnum(ARG_TYPE, type)
        name?.let { putString(ARG_NAME, it) }
        publicKey?.let { putString(ARG_PUBLIC_KEY, it.base64()) }
        accounts?.let { putParcelableArrayList(ARG_ACCOUNTS, ArrayList(it)) }
        ledgerConnectData?.let { putParcelable(ARG_LEDGER_CONNECT_DATA, it) }
        keystone?.let { putParcelable(ARG_KEYSTONE, it) }
    }

}