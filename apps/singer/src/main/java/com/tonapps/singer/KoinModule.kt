package com.tonapps.singer

import com.tonapps.singer.core.account.AccountRepository
import com.tonapps.singer.screen.create.CreateViewModel
import com.tonapps.singer.screen.root.RootViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val koinModel = module {
    single { AccountRepository(get()) }
    viewModel { RootViewModel(get()) }
    viewModel { CreateViewModel(get()) }
}