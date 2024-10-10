package com.tonapps.tonkeeper.koin

import android.content.Context
import androidx.work.WorkerParameters
import com.tonapps.tonkeeper.manager.push.PushManager
import com.tonapps.tonkeeper.worker.DAppPushToggleWorker
import com.tonapps.tonkeeper.worker.PushToggleWorker
import com.tonapps.wallet.data.account.AccountRepository
import org.koin.androidx.workmanager.dsl.worker
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

val workerModule = module {
    worker { DAppPushToggleWorker(get(), get(), get(), get(), get()) }
    worker { PushToggleWorker(get<Context>(), get<WorkerParameters>(), get<AccountRepository>(), get<PushManager>()) }
}