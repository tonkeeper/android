package com.tonapps.tonkeeper.ui.screen.init

import android.os.Bundle
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.publicKeyFromBase64
import com.tonapps.extensions.getEnum
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.extensions.putEnum
import com.tonapps.ledger.ton.LedgerConnectData
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import com.tonapps.blockchain.model.legacy.WalletEntity
import org.ton.kotlin.crypto.PublicKeyEd25519
import uikit.base.BaseArgs

data class InitArgs(
    val type: Type,
    val name: String?,
    val publicKey: PublicKeyEd25519?,
    val ledgerConnectData: LedgerConnectData?,
    val accounts: List<AccountItem>?,
    val keystone: WalletEntity.Keystone?,
    val watchRecoveryAccountId: String? = null,
    val withNew: Boolean = true,
    val raffleSourceWalletId: String? = null,
    val importStartedTracked: Boolean = false,
    val ledgerImport: String? = null,
    val ledgerMigrationFrom: String? = null,
    val openUrlAfterCreated: String? = null,
) : BaseArgs() {

    enum class Type {
        // AddWallet opens the flow on the type selector; the concrete type is picked there.
        AddWallet, New, Import, Watch, Testnet, Signer, SignerQR, Ledger, Keystone, LedgerMultichain
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
        keystone = bundle.getParcelableCompat(ARG_KEYSTONE),
        watchRecoveryAccountId = bundle.getString(ARG_WATCH_RECOVERY_ACCOUNT_ID),
        withNew = bundle.getBoolean(ARG_WITH_NEW, true),
        raffleSourceWalletId = bundle.getString(ARG_RAFFLE_SOURCE_WALLET_ID),
        importStartedTracked = bundle.getBoolean(ARG_IMPORT_STARTED_TRACKED, false),
        ledgerImport = bundle.getString(ARG_LEDGER_IMPORT),
        ledgerMigrationFrom = bundle.getString(ARG_LEDGER_MIGRATION_FROM),
        openUrlAfterCreated = bundle.getString(ARG_OPEN_URL_AFTER_CREATED),
    )

    override fun toBundle(): Bundle = Bundle().apply {
        putEnum(ARG_TYPE, type)
        name?.let { putString(ARG_NAME, it) }
        publicKey?.let { putString(ARG_PUBLIC_KEY, it.base64()) }
        accounts?.let { putParcelableArrayList(ARG_ACCOUNTS, ArrayList(it)) }
        ledgerConnectData?.let { putParcelable(ARG_LEDGER_CONNECT_DATA, it) }
        keystone?.let { putParcelable(ARG_KEYSTONE, it) }
        watchRecoveryAccountId?.let { putString(ARG_WATCH_RECOVERY_ACCOUNT_ID, it) }
        putBoolean(ARG_WITH_NEW, withNew)
        raffleSourceWalletId?.let { putString(ARG_RAFFLE_SOURCE_WALLET_ID, it) }
        putBoolean(ARG_IMPORT_STARTED_TRACKED, importStartedTracked)
        ledgerImport?.let { putString(ARG_LEDGER_IMPORT, it) }
        ledgerMigrationFrom?.let { putString(ARG_LEDGER_MIGRATION_FROM, it) }
        openUrlAfterCreated?.let { putString(ARG_OPEN_URL_AFTER_CREATED, it) }
    }

    private companion object {
        private const val ARG_TYPE = "type"
        private const val ARG_PUBLIC_KEY = "pk"
        private const val ARG_NAME = "name"
        private const val ARG_LEDGER_CONNECT_DATA = "ledger_connect_data"
        private const val ARG_ACCOUNTS = "accounts"
        private const val ARG_KEYSTONE = "keystone"
        private const val ARG_WATCH_RECOVERY_ACCOUNT_ID = "watch_recovery_account_id"
        private const val ARG_WITH_NEW = "with_new"
        private const val ARG_RAFFLE_SOURCE_WALLET_ID = "raffle_source_wallet_id"
        private const val ARG_IMPORT_STARTED_TRACKED = "import_started_tracked"
        private const val ARG_LEDGER_IMPORT = "ledger_import"
        private const val ARG_LEDGER_MIGRATION_FROM = "ledger_migration_from"
        private const val ARG_OPEN_URL_AFTER_CREATED = "open_url_after_created"
    }
}
