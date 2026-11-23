package com.tonapps.perps.data

import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.core.helper.EnvironmentHelper
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.core.TradingAPI
import com.tonapps.wallet.testing.BaseUnitTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogSearchRepositoryTest : BaseUnitTest() {

    private var requestUrl: HttpUrl? = null

    @Test
    fun theChainsOfTheFirstPageBecomeTheAvailableNetworks() = runTest {
        val page = search("""{"items":[],"data_freshness_sec":0,"chains":["arb","eth","ton","tron"]}""")

        assertEquals(
            listOf(Network.Type.Arbitrum, Network.Type.Ethereum, Network.Type.Ton, Network.Type.Tron),
            page.availableNetworks,
        )
    }

    @Test
    fun aChainThisBuildDoesNotKnowIsLeftOut() = runTest {
        val page = search("""{"items":[],"data_freshness_sec":0,"chains":["future_chain","ton"]}""")

        assertEquals(listOf(Network.Type.Ton), page.availableNetworks)
    }

    @Test
    fun anEmptyChainsListLeavesNoNetworks() = runTest {
        val page = search("""{"items":[],"data_freshness_sec":0,"chains":[]}""")

        assertEquals(emptyList<Network.Type>(), page.availableNetworks)
    }

    @Test
    fun aResponseWithoutChainsLeavesTheNetworksUnknown() = runTest {
        val page = search("""{"items":[],"data_freshness_sec":0}""")

        assertNull(page.availableNetworks)
    }

    @Test
    fun allWithoutAChainAsksForPerps() = runTest {
        search(EMPTY_BODY, showPerps = true)

        assertEquals("true", checkNotNull(requestUrl).queryParameter(SHOW_PERPS_PARAM))
    }

    @Test
    fun anotherFilterDoesNotAskForPerps() = runTest {
        search(EMPTY_BODY, filter = PerpsMarketFilter.STOCKS, showPerps = true)

        assertNull(checkNotNull(requestUrl).queryParameter(SHOW_PERPS_PARAM))
    }

    @Test
    fun allWithAChainDoesNotAskForPerps() = runTest {
        search(EMPTY_BODY, chain = "eth", showPerps = true)

        assertNull(checkNotNull(requestUrl).queryParameter(SHOW_PERPS_PARAM))
    }

    private suspend fun search(
        body: String,
        filter: PerpsMarketFilter = PerpsMarketFilter.ALL,
        chain: String? = null,
        showPerps: Boolean = false,
    ): CatalogSearchPage {
        val httpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { interceptorChain ->
                requestUrl = interceptorChain.request().url
                Response.Builder()
                    .request(interceptorChain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(HTTP_OK)
                    .message("OK")
                    .body(body.toResponseBody(JSON_MEDIA_TYPE))
                    .build()
            })
            .build()
        val api = mockk<API>()
        every { api.trading } returns TradingAPI(BASE_URL, httpClient)
        val repository = CatalogSearchRepository(api, mockk<EnvironmentHelper>(relaxed = true), mockk(relaxed = true))

        val result = repository.search(
            query = "",
            filter = filter,
            sort = CatalogSearchSort.MARKET_CAP,
            chain = chain,
            showPerps = showPerps,
            currency = "usd",
            cursor = null,
        )
        return checkNotNull(result.getOrNull())
    }

    private companion object {
        const val BASE_URL = "https://trading.test"
        const val HTTP_OK = 200
        const val SHOW_PERPS_PARAM = "show_perps"
        const val EMPTY_BODY = """{"items":[],"data_freshness_sec":0}"""
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
