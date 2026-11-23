package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowBalanceBucket
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowFrom
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowLedgerModel
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowLedgerTransport
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowPairingAction
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowPairingEntry
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowPairingExitReason
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowPairingLastStep
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowSource
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletMode
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletSource

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class WalletFlowImpl(
    private val eventExecutor: EventExecutor,
) : Events.WalletFlow {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * add_wallet_menu_view
     *
     * User sees the Add Wallet menu listing the available ways to add a wallet (New Wallet, Existing Wallet, Pair Signer, Pair with Keystone, Pair with Ledger, ...) — the shared entry point into the create and import flows, both during onboarding and from the main app (wallet switcher). Covers the blind spot of users who open the menu and leave without picking any option. The option the user picks is not a separate event — it is read from the follow-up wallet_create_started or wallet_import_started {wallet_source}. from tells the onboarding and main-app contexts apart.

     */
    @AnyThread
    override fun addWalletMenuView(from: WalletFlowFrom) {
        trackEvent("add_wallet_menu_view", hashMapOf("from" to from.key))
    }

    /**
     * wallet_create_started
     *
     * User tapped "Create new wallet", entering the wallet-creation flow
     */
    @AnyThread
    override fun walletCreateStarted(walletMode: WalletFlowWalletMode, from: WalletFlowFrom) {
        trackEvent("wallet_create_started", hashMapOf("wallet_mode" to walletMode.key, "from" to from.key))
    }

    /**
     * wallet_backup_started
     *
     * User is presented with the "Back up your recovery phrase" screen — during wallet creation or when entering the backup flow later from the wallet main screen setup section or settings (see source)

     */
    @AnyThread
    override fun walletBackupStarted(walletMode: WalletFlowWalletMode, source: WalletFlowSource) {
        trackEvent("wallet_backup_started", hashMapOf("wallet_mode" to walletMode.key, "source" to source.key))
    }

    /**
     * wallet_backup_skip
     *
     * User skipped the recovery-phrase backup from the backup screen and proceeds to finish wallet creation without backing up

     */
    @AnyThread
    override fun walletBackupSkip(walletMode: WalletFlowWalletMode, source: WalletFlowSource) {
        trackEvent("wallet_backup_skip", hashMapOf("wallet_mode" to walletMode.key, "source" to source.key))
    }

    /**
     * wallet_backup_success
     *
     * User completed the recovery-phrase backup successfully (passed the seed-phrase check). source tells backup during creation apart from a backup finished later.

     */
    @AnyThread
    override fun walletBackupSuccess(walletMode: WalletFlowWalletMode, source: WalletFlowSource) {
        trackEvent("wallet_backup_success", hashMapOf("wallet_mode" to walletMode.key, "source" to source.key))
    }

    /**
     * wallet_backup_error
     *
     * Backup attempt failed (e.g. recovery-phrase confirmation mismatch). Error fields use the same type/code/message shape as op_terminal for consistent grouping.

     */
    @AnyThread
    override fun walletBackupError(
        walletMode: WalletFlowWalletMode,
        source: WalletFlowSource,
        errorType: String?,
        errorCode: Int?,
        errorMessage: String?
    ) {
        val props = hashMapOf<String, Any>("wallet_mode" to walletMode.key, "source" to source.key)
        errorType?.let { props["error_type"] = it }
        errorCode?.let { props["error_code"] = it }
        errorMessage?.let { props["error_message"] = it }
        trackEvent("wallet_backup_error", props)
    }

    /**
     * wallet_create_success
     *
     * Wallet creation finished. Reached either after skipping backup (wallet_backup_skip) or after completing it (wallet_backup_success).

     */
    @AnyThread
    override fun walletCreateSuccess(
        walletMode: WalletFlowWalletMode,
        backedUp: Boolean,
        from: WalletFlowFrom
    ) {
        val props = hashMapOf(
            "wallet_mode" to walletMode.key,
            "backed_up" to backedUp,
            "from" to from.key
        )
        trackEvent("wallet_create_success", props)
    }

    /**
     * wallet_import_started
     *
     * User tapped "Import existing wallet", entering the import flow. wallet_source says which kind of account is being imported, matching wallet_import_success / wallet_import_error. For Signer, Keystone and Ledger it is sent on the tap of the pairing option in the Add Wallet menu, before the pairing screen; a multichain Ledger is wallet_mode = multi. pairing_entry identifies the pairing entry path when known; Signer deeplink and app QR scanner entries can bypass the menu and signer-scanned step. Moving a TON-only Ledger wallet to a multichain one is not an import and does not send it (ledger_migration_start).

     */
    @AnyThread
    override fun walletImportStarted(
        walletMode: WalletFlowWalletMode,
        walletSource: WalletFlowWalletSource,
        from: WalletFlowFrom,
        pairingEntry: WalletFlowPairingEntry?
    ) {
        val props = hashMapOf<String, Any>(
            "wallet_mode" to walletMode.key,
            "wallet_source" to walletSource.key,
            "from" to from.key
        )
        pairingEntry?.let { props["pairing_entry"] = it.key }
        trackEvent("wallet_import_started", props)
    }

    /**
     * wallet_import_pairing_action
     *
     * First action on a Signer, Keystone or Ledger pairing screen. Send once per screen visit, on the first qualifying action only; a new visit can send again. It precedes the scanned or Ledger connected step when those steps occur; leaving without an action sends no action event.

     */
    @AnyThread
    override fun walletImportPairingAction(
        walletMode: WalletFlowWalletMode,
        walletSource: WalletFlowWalletSource,
        from: WalletFlowFrom,
        pairingAction: WalletFlowPairingAction
    ) {
        val props = hashMapOf(
            "wallet_mode" to walletMode.key,
            "wallet_source" to walletSource.key,
            "from" to from.key,
            "pairing_action" to pairingAction.key
        )
        trackEvent("wallet_import_pairing_action", props)
    }

    /**
     * wallet_import_ledger_device_found
     *
     * A Ledger is discovered by the import search, before connection. Send once per discovered device per pairing-screen visit, even if search reports it repeatedly; another device or a new visit can send again. Discovery does not imply wallet_import_ledger_bluetooth_connected or wallet_import_ledger_usb_connected will follow.

     */
    @AnyThread
    override fun walletImportLedgerDeviceFound(
        walletMode: WalletFlowWalletMode,
        walletSource: WalletFlowWalletSource,
        from: WalletFlowFrom,
        ledgerTransport: WalletFlowLedgerTransport,
        ledgerModel: WalletFlowLedgerModel?
    ) {
        val props = hashMapOf<String, Any>(
            "wallet_mode" to walletMode.key,
            "wallet_source" to walletSource.key,
            "from" to from.key,
            "ledger_transport" to ledgerTransport.key
        )
        ledgerModel?.let { props["ledger_model"] = it.key }
        trackEvent("wallet_import_ledger_device_found", props)
    }

    /**
     * wallet_import_pairing_exit
     *
     * User leaves a Signer, Keystone or Ledger pairing screen without a successful import. Send once per unsuccessful screen visit, including leaving before an action; do not send on a successful import. Record the furthest step reached on that visit and the reason for leaving. An error can also emit wallet_import_error; the exit is sent on leaving, not on every error or retry.

     */
    @AnyThread
    override fun walletImportPairingExit(
        walletMode: WalletFlowWalletMode,
        walletSource: WalletFlowWalletSource,
        from: WalletFlowFrom,
        pairingLastStep: WalletFlowPairingLastStep,
        pairingExitReason: WalletFlowPairingExitReason
    ) {
        val props = hashMapOf(
            "wallet_mode" to walletMode.key,
            "wallet_source" to walletSource.key,
            "from" to from.key,
            "pairing_last_step" to pairingLastStep.key,
            "pairing_exit_reason" to pairingExitReason.key
        )
        trackEvent("wallet_import_pairing_exit", props)
    }

    /**
     * wallet_import_mnemonic_accepted
     *
     * Recovery phrase passed validation and the import continued. An invalid phrase is wallet_import_error, not this event. A phrase that belongs to a wallet already in the app does not emit it.

     */
    @AnyThread
    override fun walletImportMnemonicAccepted(
        walletMode: WalletFlowWalletMode,
        walletSource: WalletFlowWalletSource,
        from: WalletFlowFrom
    ) {
        val props = hashMapOf(
            "wallet_mode" to walletMode.key,
            "wallet_source" to walletSource.key,
            "from" to from.key
        )
        trackEvent("wallet_import_mnemonic_accepted", props)
    }

    /**
     * wallet_import_signer_scanned
     *
     * Signer link QR was scanned and produced a public key. Opening Signer from a deeplink does not emit this event.

     */
    @AnyThread
    override fun walletImportSignerScanned(
        walletMode: WalletFlowWalletMode,
        walletSource: WalletFlowWalletSource,
        from: WalletFlowFrom
    ) {
        val props = hashMapOf(
            "wallet_mode" to walletMode.key,
            "wallet_source" to walletSource.key,
            "from" to from.key
        )
        trackEvent("wallet_import_signer_scanned", props)
    }

    /**
     * wallet_import_keystone_scanned
     *
     * Keystone QR was scanned and produced a public key.
     */
    @AnyThread
    override fun walletImportKeystoneScanned(
        walletMode: WalletFlowWalletMode,
        walletSource: WalletFlowWalletSource,
        from: WalletFlowFrom
    ) {
        val props = hashMapOf(
            "wallet_mode" to walletMode.key,
            "wallet_source" to walletSource.key,
            "from" to from.key
        )
        trackEvent("wallet_import_keystone_scanned", props)
    }

    /**
     * wallet_import_ledger_bluetooth_connected
     *
     * Ledger device connected over Bluetooth during import. Emitted again only after a disconnect, when the device connects once more. For a multichain Ledger: sent for each Ledger the user picks, once it connects and its accounts start being read; a Ledger connected by cable sends wallet_import_ledger_usb_connected instead.

     */
    @AnyThread
    override fun walletImportLedgerBluetoothConnected(
        walletMode: WalletFlowWalletMode,
        walletSource: WalletFlowWalletSource,
        from: WalletFlowFrom,
        ledgerModel: WalletFlowLedgerModel?
    ) {
        val props = hashMapOf<String, Any>(
            "wallet_mode" to walletMode.key,
            "wallet_source" to walletSource.key,
            "from" to from.key
        )
        ledgerModel?.let { props["ledger_model"] = it.key }
        trackEvent("wallet_import_ledger_bluetooth_connected", props)
    }

    /**
     * wallet_import_ledger_usb_connected
     *
     * A multichain Ledger the user picked connected over a USB cable during import — the counterpart of wallet_import_ledger_bluetooth_connected, and the only way Nano S and Nano S Plus connect. Sent for each Ledger the user picks, once it connects.

     */
    @AnyThread
    override fun walletImportLedgerUsbConnected(
        walletMode: WalletFlowWalletMode,
        walletSource: WalletFlowWalletSource,
        from: WalletFlowFrom,
        ledgerModel: WalletFlowLedgerModel?
    ) {
        val props = hashMapOf<String, Any>(
            "wallet_mode" to walletMode.key,
            "wallet_source" to walletSource.key,
            "from" to from.key
        )
        ledgerModel?.let { props["ledger_model"] = it.key }
        trackEvent("wallet_import_ledger_usb_connected", props)
    }

    /**
     * wallet_import_ledger_app_opened
     *
     * TON app on the connected Ledger reported itself open during import. A multichain Ledger reads the TON app first, so this is also the point where its other apps start being read.

     */
    @AnyThread
    override fun walletImportLedgerAppOpened(
        walletMode: WalletFlowWalletMode,
        walletSource: WalletFlowWalletSource,
        from: WalletFlowFrom,
        ledgerModel: WalletFlowLedgerModel?
    ) {
        val props = hashMapOf<String, Any>(
            "wallet_mode" to walletMode.key,
            "wallet_source" to walletSource.key,
            "from" to from.key
        )
        ledgerModel?.let { props["ledger_model"] = it.key }
        trackEvent("wallet_import_ledger_app_opened", props)
    }

    /**
     * wallet_import_ledger_accounts_view
     *
     * The accounts of a multichain Ledger were read and the account picker listed them, with their addresses in the networks of every app read from the device. ledger_apps is the apps whose addresses were read: an app that is not installed, is too old, or that the user declined on the device is skipped, its networks are not added, and it sends ledger_discovery_error instead. Sent each time the list renders, Load more included; a TON-only Ledger does not send it.

     */
    @AnyThread
    override fun walletImportLedgerAccountsView(
        walletMode: WalletFlowWalletMode,
        walletSource: WalletFlowWalletSource,
        from: WalletFlowFrom,
        ledgerModel: WalletFlowLedgerModel?,
        ledgerApps: String
    ) {
        val props = hashMapOf<String, Any>(
            "wallet_mode" to walletMode.key,
            "wallet_source" to walletSource.key,
            "from" to from.key,
            "ledger_apps" to ledgerApps
        )
        ledgerModel?.let { props["ledger_model"] = it.key }
        trackEvent("wallet_import_ledger_accounts_view", props)
    }

    /**
     * wallet_import_error
     *
     * Wallet import attempt failed (e.g. invalid recovery phrase). Error fields use the same type/code/message shape as op_terminal for consistent grouping. For a multichain Ledger error_type is a LedgerFailureType value (ledger-flow.yaml).

     */
    @AnyThread
    override fun walletImportError(
        walletMode: WalletFlowWalletMode,
        walletSource: WalletFlowWalletSource,
        from: WalletFlowFrom,
        errorType: String?,
        errorCode: Int?,
        errorMessage: String?,
        ledgerModel: WalletFlowLedgerModel?
    ) {
        val props = hashMapOf<String, Any>(
            "wallet_mode" to walletMode.key,
            "wallet_source" to walletSource.key,
            "from" to from.key
        )
        errorType?.let { props["error_type"] = it }
        errorCode?.let { props["error_code"] = it }
        errorMessage?.let { props["error_message"] = it }
        ledgerModel?.let { props["ledger_model"] = it.key }
        trackEvent("wallet_import_error", props)
    }

    /**
     * wallet_import_success
     *
     * User imported an existing wallet successfully. pairing_entry records the pairing entry path when known; Signer deeplink and app QR scanner entries can reach success without wallet_import_signer_scanned.

     */
    @AnyThread
    override fun walletImportSuccess(
        walletMode: WalletFlowWalletMode,
        walletSource: WalletFlowWalletSource,
        from: WalletFlowFrom,
        pairingEntry: WalletFlowPairingEntry?,
        ledgerModel: WalletFlowLedgerModel?
    ) {
        val props = hashMapOf<String, Any>(
            "wallet_mode" to walletMode.key,
            "wallet_source" to walletSource.key,
            "from" to from.key
        )
        pairingEntry?.let { props["pairing_entry"] = it.key }
        ledgerModel?.let { props["ledger_model"] = it.key }
        trackEvent("wallet_import_success", props)
    }

    /**
     * wallet_import_balance
     *
     * What the imported wallet already held, as a range in US dollars (balance_bucket): the money a user brought to Tonkeeper without a deposit. The balance counts verified assets only (dust included) at import time; several versions of one recovery phrase share one TRON account, which is counted once. Sent once per import, after wallet_import_success, as soon as the balance of the imported wallet loads (a multichain wallet's once it has finished syncing); when one import adds several wallets (several versions of the same recovery phrase), balance_bucket is the range of their sum. Not sent for a watch-only import, whose money stays with keys outside the app, for a testnet import, or when the balance could not be loaded (including a multichain wallet that failed to sync or did not sync within two minutes). Android only for now; iOS does not send it yet.

     */
    @AnyThread
    override fun walletImportBalance(
        walletMode: WalletFlowWalletMode,
        walletSource: WalletFlowWalletSource,
        from: WalletFlowFrom,
        balanceBucket: WalletFlowBalanceBucket
    ) {
        val props = hashMapOf(
            "wallet_mode" to walletMode.key,
            "wallet_source" to walletSource.key,
            "from" to from.key,
            "balance_bucket" to balanceBucket.key
        )
        trackEvent("wallet_import_balance", props)
    }
}
