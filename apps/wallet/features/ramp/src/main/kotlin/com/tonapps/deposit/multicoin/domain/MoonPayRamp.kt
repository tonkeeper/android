package com.tonapps.deposit.multicoin.domain

import com.tonapps.deposit.multicoin.moonpay.MoonPayConnectionException
import com.tonapps.deposit.multicoin.moonpay.MoonPayMethodSupport
import com.tonapps.deposit.multicoin.moonpay.domain.MoonPayButtonPayments
import com.tonapps.deposit.multicoin.moonpay.crypto.MoonPayCryptoException
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayConnectionStatus

class MoonPayRamp(
    val payments: RampPayments,
    val check: suspend () -> MoonPayAccess,
    val connect: suspend () -> MoonPaySignIn,
    val methodSupport: MoonPayMethodSupport,
    val buttons: MoonPayButtonPayments?,
)

sealed interface MoonPayAccess {
    data object Connected : MoonPayAccess

    data object SignInRequired : MoonPayAccess

    data object Unavailable : MoonPayAccess

    data object Unreachable : MoonPayAccess

    companion object {

        fun of(result: Result<*>): MoonPayAccess {
            val error = result.exceptionOrNull() ?: return Connected
            if (error is MoonPayCryptoException) {
                return Unavailable
            }
            if (error !is MoonPayConnectionException.Rejected) {
                return Unreachable
            }
            return when (error.status) {
                MoonPayConnectionStatus.ConnectionRequired,
                MoonPayConnectionStatus.TermsAcceptanceRequired,
                -> if (error.signInOffered) {
                    SignInRequired
                } else {
                    Unavailable
                }
                MoonPayConnectionStatus.Active,
                MoonPayConnectionStatus.Pending,
                MoonPayConnectionStatus.Unavailable,
                MoonPayConnectionStatus.Failed,
                -> Unavailable
            }
        }
    }
}

sealed interface MoonPaySignIn {
    data object SignedIn : MoonPaySignIn

    data object Closed : MoonPaySignIn

    data object Refused : MoonPaySignIn

    data class Failed(val error: Throwable) : MoonPaySignIn

    companion object {

        fun of(result: Result<*>): MoonPaySignIn {
            val error = result.exceptionOrNull() ?: return SignedIn
            return when (error) {
                is MoonPayConnectionException.Cancelled -> Closed
                is MoonPayCryptoException -> Refused
                is MoonPayConnectionException.Rejected -> when (error.status) {
                    MoonPayConnectionStatus.Failed,
                    MoonPayConnectionStatus.Unavailable,
                    -> Refused
                    MoonPayConnectionStatus.Active,
                    MoonPayConnectionStatus.ConnectionRequired,
                    MoonPayConnectionStatus.TermsAcceptanceRequired,
                    MoonPayConnectionStatus.Pending,
                    -> Failed(error)
                }
                else -> Failed(error)
            }
        }
    }
}
