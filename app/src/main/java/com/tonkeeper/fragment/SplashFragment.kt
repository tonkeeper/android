package com.tonkeeper.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.api.account.AccountRepository
import com.tonkeeper.api.collectibles.CollectiblesRepository
import com.tonkeeper.api.event.EventRepository
import com.tonkeeper.api.jetton.JettonRepository
import com.tonkeeper.core.currency.CurrencyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.navigation.Navigation.Companion.nav

class SplashFragment: Fragment(R.layout.fragment_splash) {

    private val accountRepository = AccountRepository()
    private val jettonRepository = JettonRepository()
    private val collectiblesRepository = CollectiblesRepository()
    private val eventRepository = EventRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            // initWallet()
            App.fiat.init(App.settings.country)

            withContext(Dispatchers.Main) {
                nav()?.init(false)
            }
        }
    }

    private suspend fun initWallet() = withContext(Dispatchers.IO) {
        val wallet = App.walletManager.getWalletInfo() ?: return@withContext
        val address = wallet.address

        CurrencyManager.getInstance().init(address)

        accountRepository.get(address)
        jettonRepository.get(address)
        collectiblesRepository.get(address)

        eventRepository.get(address)
    }
}