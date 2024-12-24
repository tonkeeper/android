package com.tonapps.tonkeeper.koin

import com.tonapps.tonkeeper.worker.ApkDownloadWorker
import com.tonapps.tonkeeper.worker.DAppPushToggleWorker
import com.tonapps.tonkeeper.worker.PushToggleWorker
import com.tonapps.tonkeeper.worker.WidgetUpdaterWorker
import org.koin.androidx.workmanager.dsl.worker
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

val workerModule = module {
    workerOf(::DAppPushToggleWorker)
    workerOf(::PushToggleWorker)
    workerOf(::WidgetUpdaterWorker)
    workerOf(::ApkDownloadWorker)
}