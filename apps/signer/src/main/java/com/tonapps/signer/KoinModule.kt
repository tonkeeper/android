package com.tonapps.signer

import com.tonapps.signer.core.di.coreModule
import com.tonapps.signer.screen.change.ChangeViewModel
import com.tonapps.signer.screen.create.CreateViewModel
import com.tonapps.signer.screen.key.KeyViewModel
import com.tonapps.signer.screen.main.MainViewModel
import com.tonapps.signer.screen.root.RootViewModel
import com.tonapps.signer.screen.sign.SignViewModel
import com.tonapps.signer.vault.SignerVault
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val koinModel = module {
    single(createdAtStart = false) { SignerVault(androidContext()) }

    includes(coreModule)

    viewModel { RootViewModel(get(), get()) }
    viewModel { parameters -> CreateViewModel(import = parameters.get(), get(), get(), get()) }
    viewModel { MainViewModel(get()) }
    viewModel { parameters -> KeyViewModel(id = parameters.get(), get(), get()) }
    viewModel { parameters -> SignViewModel(
        id = parameters.get(),
        unsignedBody = parameters.get(),
        v = parameters.get(),
        seqno = parameters.get(),
        network = parameters.get(),
        get(),
        get()
    ) }
    viewModel { ChangeViewModel(get(), get()) }
}