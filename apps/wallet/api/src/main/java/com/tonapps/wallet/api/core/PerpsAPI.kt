package com.tonapps.wallet.api.core

import io.perpsapi.apis.AccountApi
import io.perpsapi.apis.ActivityApi
import io.perpsapi.apis.FundingApi
import io.perpsapi.apis.IntegratorApi
import io.perpsapi.apis.OrderbookApi
import io.perpsapi.apis.OrdersApi
import io.perpsapi.apis.PortfolioApi
import io.perpsapi.apis.PositionsApi
import io.perpsapi.apis.ScreensApi
import io.perpsapi.apis.TransactionsApi
import okhttp3.OkHttpClient

class PerpsAPI(
    basePath: String,
    okHttpClient: OkHttpClient
) {
    val screens: ScreensApi by lazy { ScreensApi(basePath, okHttpClient) }
    val positions: PositionsApi by lazy { PositionsApi(basePath, okHttpClient) }
    val portfolio: PortfolioApi by lazy { PortfolioApi(basePath, okHttpClient) }
    val orders: OrdersApi by lazy { OrdersApi(basePath, okHttpClient) }
    val transactions: TransactionsApi by lazy { TransactionsApi(basePath, okHttpClient) }
    val orderbook: OrderbookApi by lazy { OrderbookApi(basePath, okHttpClient) }
    val account: AccountApi by lazy { AccountApi(basePath, okHttpClient) }
    val integrator: IntegratorApi by lazy { IntegratorApi(basePath, okHttpClient) }
    val funding: FundingApi by lazy { FundingApi(basePath, okHttpClient) }
    val activity: ActivityApi by lazy { ActivityApi(basePath, okHttpClient) }
}
