package com.tonapps.mvi

import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
object Mvi {

    data class Config(
        val useThreadCheck: Boolean = false,
        val isFastFail: Boolean = false
    )

    @Volatile
    private var config = Config()
    private val isInit = AtomicBoolean(false)

    fun init(config: Config) {
        if (isInit.compareAndSet(expectedValue = false, newValue = true)) {
            this.config = config
        }
    }

    fun config(): Config {
        return config
    }
}
