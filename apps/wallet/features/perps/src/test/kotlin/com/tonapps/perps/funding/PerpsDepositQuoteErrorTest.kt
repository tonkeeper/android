package com.tonapps.perps.funding

import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLoadException
import com.tonapps.wallet.data.multichain.lighter.PerpsQuoteError
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/** Which message the amount screen shows for every way a deposit quote can fail. */
class PerpsDepositQuoteErrorTest {

    @Test
    fun quoteFailuresMapToWhatTheAmountScreenShows() {
        val cases = listOf(
            PerpsDepositException.InsufficientBalance() to PerpsDepositQuoteError.InsufficientBalance,
            PerpsDepositException.QuoteRejected(PerpsQuoteError.MalformedAmount("x")) to PerpsDepositQuoteError.QuoteFailed,
            PerpsDepositException.WalletUnavailable() to PerpsDepositQuoteError.Failed,
            PerpsLoadException(PerpsError.Network) to PerpsDepositQuoteError.ServiceUnavailable,
            PerpsLoadException(PerpsError.ServiceUnavailable) to PerpsDepositQuoteError.ServiceUnavailable,
            PerpsLoadException(PerpsError.BadRequest) to PerpsDepositQuoteError.QuoteFailed,
            IOException("offline") to PerpsDepositQuoteError.ServiceUnavailable,
            IllegalStateException("boom") to PerpsDepositQuoteError.Failed,
        )

        cases.forEach { (thrown, expected) ->
            assertEquals(thrown::class.simpleName, expected, thrown.toDepositQuoteError())
        }
    }
}
