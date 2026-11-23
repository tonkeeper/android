package com.tonapps.deposit.multicoin.domain

import com.tonapps.deposit.multicoin.moonpay.MoonPayConnectionException
import com.tonapps.deposit.multicoin.moonpay.crypto.MoonPayCryptoException
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayConnectionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class MoonPayRampTest {

    @Test
    fun `a silent check needs a sign-in only when MoonPay asks for one, and a failure to reach it is not a refusal`() {
        assertEquals(MoonPayAccess.Connected, MoonPayAccess.of(Result.success(Unit)))
        assertEquals(MoonPayAccess.SignInRequired, MoonPayAccess.of(rejected(MoonPayConnectionStatus.ConnectionRequired)))
        assertEquals(MoonPayAccess.SignInRequired, MoonPayAccess.of(rejected(MoonPayConnectionStatus.TermsAcceptanceRequired)))
        assertEquals(
            MoonPayAccess.Unavailable,
            MoonPayAccess.of(Result.failure<Unit>(MoonPayConnectionException.Rejected(MoonPayConnectionStatus.ConnectionRequired, signInOffered = false))),
        )
        assertEquals(MoonPayAccess.Unavailable, MoonPayAccess.of(rejected(MoonPayConnectionStatus.Failed)))
        assertEquals(MoonPayAccess.Unavailable, MoonPayAccess.of(rejected(MoonPayConnectionStatus.Unavailable)))
        assertEquals(MoonPayAccess.Unavailable, MoonPayAccess.of(rejected(MoonPayConnectionStatus.Pending)))
        assertEquals(MoonPayAccess.Unavailable, MoonPayAccess.of(Result.failure<Unit>(MoonPayCryptoException.InvalidTag())))
        assertEquals(MoonPayAccess.Unreachable, MoonPayAccess.of(Result.failure<Unit>(MoonPayConnectionException.TimedOut())))
        assertEquals(MoonPayAccess.Unreachable, MoonPayAccess.of(Result.failure<Unit>(IOException("offline"))))
    }

    @Test
    fun `a sign-in closed by the customer is no failure, one that ends without a connection is`() {
        assertEquals(MoonPaySignIn.SignedIn, MoonPaySignIn.of(Result.success(Unit)))
        assertEquals(MoonPaySignIn.Closed, MoonPaySignIn.of(Result.failure<Unit>(MoonPayConnectionException.Cancelled())))
        assertEquals(MoonPaySignIn.Refused, MoonPaySignIn.of(Result.failure<Unit>(MoonPayCryptoException.InvalidTag())))
        assertEquals(MoonPaySignIn.Refused, MoonPaySignIn.of(rejected(MoonPayConnectionStatus.Failed)))
        assertEquals(MoonPaySignIn.Refused, MoonPaySignIn.of(rejected(MoonPayConnectionStatus.Unavailable)))
        assertTrue(MoonPaySignIn.of(rejected(MoonPayConnectionStatus.ConnectionRequired)) is MoonPaySignIn.Failed)
        assertTrue(MoonPaySignIn.of(rejected(MoonPayConnectionStatus.TermsAcceptanceRequired)) is MoonPaySignIn.Failed)
        assertTrue(MoonPaySignIn.of(rejected(MoonPayConnectionStatus.Pending)) is MoonPaySignIn.Failed)
        assertTrue(MoonPaySignIn.of(Result.failure<Unit>(IOException("offline"))) is MoonPaySignIn.Failed)
    }

    private fun rejected(status: MoonPayConnectionStatus): Result<Unit> {
        return Result.failure(MoonPayConnectionException.Rejected(status))
    }
}