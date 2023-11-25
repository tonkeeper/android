package com.tonkeeper.fragment.settings.list.item

open class SettingsIdItem(
    type: Int,
    open val id: Int
): SettingsItem(type) {

    companion object {
        const val LOGOUT_ID = 1
        const val CURRENCY_ID = 2
        const val SECURITY_ID = 3
        const val LEGAL_ID = 4
        const val TERMS_ID = 5
        const val PRIVACY_ID = 6
        const val LICENSES_FONT_ID = 7
        const val USE_BIOMETRIC_ID = 8
        const val SUPPORT_ID = 9
        const val TONKEEPER_NEWS_ID = 10
        const val CONTACT_US_ID = 11
        const val MANAGE_WALLETS_ID = 12
    }
}