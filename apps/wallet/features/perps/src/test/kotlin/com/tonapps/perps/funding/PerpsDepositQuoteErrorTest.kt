package com.tonapps.perps.funding

import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLoadException
import com.tonapps.wallet.data.multichain.lighter.PerpsQuoteError
import io.infrastructure.ClientError
import io.infrastructure.ClientException
import io.infrastructure.ServerError
import io.infrastructure.ServerException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal

/** Which message the amount screen shows for every way a deposit quote can fail. */
class PerpsDepositQuoteErrorTest {

    @Test
    fun quoteFailuresMapToWhatTheAmountScreenShows() {
        val cases = listOf(
            PerpsDepositException.InsufficientBalance() to PerpsDepositQuoteError.InsufficientBalance,
            PerpsDepositException.QuoteRejected(PerpsQuoteError.MalformedAmount("x")) to PerpsDepositQuoteError.QuoteFailed,
            PerpsDepositException.WalletUnavailable() to PerpsDepositQuoteError.Failed,
            PerpsDepositException.AddressBlocked() to PerpsDepositQuoteError.AddressBlocked,
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

    @Test
    fun aSenderTheProviderRefusesIsBlockedWhateverTheBackendSaysOfARetry() {
        listOf(false, true).forEach { retryable ->
            val body = """{"code":"validation_error","retryable":$retryable,"details":{"reason":"invalid_sender_address"}}"""
            val thrown = ClientException(statusCode = 400, response = ClientError<Any>(body = body, statusCode = 400))

            assertEquals(PerpsDepositQuoteError.AddressBlocked, thrown.toDepositQuoteError())
        }
    }

    @Test
    fun aRegionRefusalIsToldByItsCodeWhateverTheStatus() {
        listOf(400, 409).forEach { status ->
            val thrown = ClientException(statusCode = status, response = ClientError<Any>(body = REGION_RESTRICTED_BODY, statusCode = status))

            assertTrue(status.toString(), thrown.isRegionRestricted())
        }
    }

    @Test
    fun otherFailuresAreNoRegionRefusal() {
        val senderBody = """{"code":"validation_error","retryable":false,"details":{"reason":"invalid_sender_address"}}"""
        val cases = listOf(
            ClientException(statusCode = 400, response = ClientError<Any>(body = senderBody, statusCode = 400)),
            regionCheckUnavailable(),
            IOException("offline"),
            IllegalStateException("boom"),
            PerpsDepositException.AddressBlocked(),
        )

        cases.forEach { thrown ->
            assertFalse(thrown::class.simpleName, thrown.isRegionRestricted())
        }
    }

    @Test
    fun anUnavailableRegionCheckIsAServiceToRetry() {
        assertEquals(PerpsDepositQuoteError.ServiceUnavailable, regionCheckUnavailable().toDepositQuoteError())
    }

    @Test
    fun aMissingNetworkFeeNamesTheCoinAndTheAmountItNeeds() {
        val thrown = PerpsDepositException.InsufficientNetworkFee(symbol = "GRAM", required = BigDecimal("1.031"))

        assertEquals(PerpsDepositQuoteError.InsufficientNetworkFee("GRAM", BigDecimal("1.031")), thrown.toDepositQuoteError())
    }

    @Test
    fun onlyRefusalsOfTheAmountItselfAreToldNextToTheAmount() {
        val cases = mapOf(
            PerpsDepositQuoteError.BelowMinimum(BigDecimal("1.08")) to true,
            PerpsDepositQuoteError.BelowMinimum(null) to true,
            PerpsDepositQuoteError.InsufficientBalance to true,
            PerpsDepositQuoteError.InsufficientNetworkFee("GRAM", BigDecimal("1.031")) to true,
            PerpsDepositQuoteError.AddressBlocked to true,
            PerpsDepositQuoteError.ServiceUnavailable to false,
            PerpsDepositQuoteError.QuoteFailed to false,
            PerpsDepositQuoteError.Failed to false,
        )

        cases.forEach { (error, refusesAmount) ->
            assertEquals(error.toString(), refusesAmount, error.refusesAmount)
        }
    }

    private fun regionCheckUnavailable(): ServerException {
        val body = """{"code":"region_check_unavailable","retryable":true}"""
        return ServerException(statusCode = 503, response = ServerError<Any>(body = body, statusCode = 503))
    }

    private companion object {
        const val REGION_RESTRICTED_BODY =
            """{"code":"region_restricted","message":"not available in this region","retryable":false,"details":null}"""
    }
}
