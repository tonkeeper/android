package com.tonapps.wallet.data.passcode

import com.tonapps.wallet.data.passcode.source.PasscodeStore
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val passcodeModule = module {
    singleOf(::PasscodeStore)
    singleOf(::PasscodeHelper)
    singleOf(::PasscodeManager)
}