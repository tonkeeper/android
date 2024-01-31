package com.tonapps.signer.extensions

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import org.koin.android.ext.android.getKoinScope
import org.koin.androidx.viewmodel.resolveViewModel
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.parameter.ParametersHolder
import org.koin.core.qualifier.Qualifier
import uikit.base.BaseDialog

@MainThread
inline fun <reified T : ViewModel> BaseDialog.viewModel(
    qualifier: Qualifier? = null,
    noinline ownerProducer: () -> ViewModelStoreOwner = { this },
    noinline extrasProducer: (() -> CreationExtras)? = null,
    noinline parameters: (() -> ParametersHolder)? = null,
): Lazy<T> {
    return lazy(LazyThreadSafetyMode.NONE) {
        getViewModel(qualifier, ownerProducer, extrasProducer, parameters)
    }
}

@OptIn(KoinInternalApi::class)
inline fun <reified T : ViewModel> BaseDialog.getViewModel(
    qualifier: Qualifier? = null,
    noinline ownerProducer: () -> ViewModelStoreOwner = { this },
    noinline extrasProducer: (() -> CreationExtras)? = null,
    noinline parameters: (() -> ParametersHolder)? = null,
): T {
    return resolveViewModel(
        T::class,
        ownerProducer().viewModelStore,
        extras = extrasProducer?.invoke() ?: this.getDefaultViewModelCreationExtras(),
        qualifier = qualifier,
        parameters = parameters,
        scope = getKoinScope()
    )
}