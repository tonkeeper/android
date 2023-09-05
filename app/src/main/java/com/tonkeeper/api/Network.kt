package com.tonkeeper.api

import android.util.Log
import com.tonkeeper.api.method.AccountMethod
import com.tonkeeper.api.method.JettonsMethod
import com.tonkeeper.api.method.NftsMethod
import com.tonkeeper.api.method.RatesMethod
import com.tonkeeper.api.model.JettonItemModel
import com.tonkeeper.extensions.toCoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object Network {

    const val ENDPOINT = "https://tonapi.io/v2/"

    val okHttpClient = OkHttpClient()

    fun newRequest(url: String) = Request.Builder().url(url)

    fun newCall(request: Request) = okHttpClient.newCall(request)

    fun request(request: Request) = newCall(request).execute()

    fun getWalletOrNull(address: String): Wallet? {
        Log.d("NetworkLog", "start")
        return try {
            getWallet(address)
        } catch (e: Exception) {
            Log.e("NetworkLog", "getWalletOrNull", e)
            null
        }
    }

    @Throws(Exception::class)
    fun getWallet(address: String): Wallet {
        val account = AccountMethod(address).execute()
        val rates = RatesMethod().execute()
        val jettons = JettonsMethod(address).execute()
        val nfts = NftsMethod(address).execute()
        val tonBalanceCoins = account.balance.toCoin()

        return Wallet(
            address = address,
            balanceTON = tonBalanceCoins,
            balanceUSD = tonBalanceCoins * rates.ton.prices.usd,
            jettons = jettons,
            rate = rates.ton.prices.usd,
            rateDiff24h = rates.ton.diff24h.usd,
            nfts = nfts
        )
    }
}