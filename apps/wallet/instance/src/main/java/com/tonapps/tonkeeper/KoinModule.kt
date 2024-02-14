package com.tonapps.tonkeeper

import com.tonapps.tonkeeper.fragment.main.MainViewModel
import com.tonapps.tonkeeper.ui.screen.name.base.NameViewModel
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeper.ui.screen.init.pager.child.phrase.PhraseViewModel
import kotlinx.coroutines.Dispatchers
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val koinModel = module {
    factory { Dispatchers.Default }
    viewModel { parameters -> NameViewModel(mode = parameters.get(), get(), get()) }
    viewModel { parameters -> InitViewModel(action = parameters.get(), argsName = parameters.get(), argsPkBase64 = parameters.get(), get()) }
    viewModel { PhraseViewModel() }
    viewModel { MainViewModel() }
}