package com.tonapps.wallet.data.passcode

import androidx.annotation.StringRes
import com.tonapps.wallet.localization.Localization

enum class PasscodeReason(@StringRes val text: Int) {
    CONFIRM_TRANSFER(Localization.passcode_reason_confirm_transfer),
    CONFIRM_SWAP(Localization.passcode_reason_confirm_swap),
    SIGN_DATA(Localization.passcode_reason_sign_data),
    DAPP_REQUEST(Localization.passcode_reason_dapp_request),
    CONNECT_DAPP(Localization.passcode_reason_connect_dapp),
    PERPS(Localization.passcode_reason_perps),
    DECRYPT_COMMENT(Localization.passcode_reason_decrypt_comment),
    SHOW_RECOVERY_PHRASE(Localization.passcode_reason_show_recovery_phrase),
    BACKUP(Localization.passcode_reason_backup),
    ENABLE_BIOMETRY(Localization.passcode_reason_enable_biometry),
    MIGRATE_WALLET(Localization.passcode_reason_migrate_wallet),
    CONFIRM_ACTION(Localization.passcode_reason_confirm_action),
    TOPUP_BATTERY(Localization.passcode_reason_topup_battery),
    ADD_WALLET(Localization.passcode_reason_add_wallet),
    DELETE_WALLET(Localization.passcode_reason_delete_wallet),
    UNLOCK_WALLET(Localization.passcode_reason_unlock_wallet),
    ADD_NETWORKS(Localization.passcode_reason_add_networks),
    WALLET_MIGRATION(Localization.passcode_reason_wallet_migration),
}