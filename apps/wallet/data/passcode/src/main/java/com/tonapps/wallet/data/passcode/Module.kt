package com.tonapps.wallet.data.passcode

import com.tonapps.wallet.data.passcode.source.PasscodeStore
import org.koin.dsl.module

val passcodeModule = module {
    single { PasscodeStore(get()) }
    single { PasscodeHelper(get(), get()) }
    single { PasscodeManager(get(), get(), get(), get()) }
}