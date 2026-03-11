package com.tonapps.log

import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.util.concurrent.Executor

data class LoggerConfig(
    val logsDir: File,
    val sharedDir: File,
    val pubKeyProvider: () -> ByteArray?,
    val executor: Executor,
    val scope: CoroutineScope,
    val archiveName: String = DEFAULT_LOG_ARCHIVE,
    val maxLength: Int = LOGGER_ENTRY_MAX_LEN
) {
    companion object {
        private const val DEFAULT_LOG_ARCHIVE = "TK.logup.zip"
        const val LOGGER_ENTRY_MAX_LEN = 6 * 1024
    }

    fun outputArchive(): File {
        return File(logsDir, archiveName)
    }

    fun outputPublicArchive(): File {
        return File(sharedDir, archiveName)
    }
}
