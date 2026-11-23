package com.tonapps.core.loyalty

import java.net.URLEncoder

private const val REFERRALS_PATH = "referrals"
private const val REFERRAL_CODE_QUERY = "referralCode"

fun loyaltyUrlOf(referralCode: String?): String {
    referralCode ?: return LOYALTY_URL
    return "$LOYALTY_URL$REFERRALS_PATH?$REFERRAL_CODE_QUERY=${URLEncoder.encode(referralCode, "UTF-8")}"
}
