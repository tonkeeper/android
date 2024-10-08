package com.tonapps.tonkeeper.koin

import com.tonapps.tonkeeper.worker.DAppPushToggleWorker
import com.tonapps.tonkeeper.worker.PushToggleWorker
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

val workerModule = module {
    workerOf(::DAppPushToggleWorker)
    workerOf(::PushToggleWorker)
}