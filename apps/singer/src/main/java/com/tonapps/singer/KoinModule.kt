package com.tonapps.singer

import com.tonapps.singer.core.account.AccountRepository
import com.tonapps.singer.screen.create.CreateViewModel
import com.tonapps.singer.screen.key.KeyViewModel
import com.tonapps.singer.screen.main.MainViewModel
import com.tonapps.singer.screen.name.NameViewModel
import com.tonapps.singer.screen.password.lock.LockViewModel
import com.tonapps.singer.screen.phrase.PhraseViewModel
import com.tonapps.singer.screen.qr.QRViewModel
import com.tonapps.singer.screen.root.RootViewModel
import com.tonapps.singer.screen.sign.SignViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val koinModel = module {
    single { AccountRepository(get()) }
    viewModel { RootViewModel(get()) }
    viewModel { parameters -> CreateViewModel(import = parameters.get(), get(), get()) }
    viewModel { MainViewModel(get()) }
    viewModel { parameters -> KeyViewModel(id = parameters.get(), get()) }
    viewModel { LockViewModel(get()) }
    viewModel { parameters -> PhraseViewModel(id = parameters.get(), get()) }
    viewModel { parameters -> NameViewModel(id = parameters.get(), get()) }
    viewModel { parameters -> SignViewModel(id = parameters.get(), boc = parameters.get(), get()) }
    viewModel { parameters -> QRViewModel(id = parameters.get(), body = parameters.get(), get()) }
}