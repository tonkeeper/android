package com.tonapps.tonkeeper.ui.screen.browser.dapp

import com.tonapps.log.L
import com.tonapps.log.LoggerConfig
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.EmptyCoroutineContext

// The bridges log, and L writes to android.util.Log until it is initialized: with no targets it
// writes nowhere, which a JVM test needs.
internal fun silenceLog() {
    L.initialize(
        LoggerConfig(
            logsDir = File("."),
            sharedDir = File("."),
            pubKeyProvider = { null },
            executor = Executor { it.run() },
            scope = CoroutineScope(EmptyCoroutineContext),
        ),
        targets = emptyList(),
    )
}
