package com.tonapps.log.targets.crash

import android.content.Context
import com.tonapps.log.L
import com.tonapps.log.LogTarget
import com.tonapps.log.LoggerConfig
import java.io.File

data class CrashFileConfig(
    val dirName: String = DEFAULT_CRASH_DIR,
    val fileName: String = DEFAULT_CRASH_FILENAME,
    val maxFilesCount: Int = DEFAULT_MAX_FILES_COUNT,
) {

    fun outputDir(root: File): File {
        return File(root, dirName)
    }

    fun outputLog(dir: File, date: String): File {
        val name = fileName.substringBeforeLast('.')
        val extension = fileName.substringAfterLast('.')
        return File(dir, "$name.$date.$extension")
    }

    companion object {
        private const val DEFAULT_CRASH_DIR = "crash"
        private const val DEFAULT_CRASH_FILENAME = "TK.crash.log"
        private const val DEFAULT_MAX_FILES_COUNT = 5
    }
}

class CrashLogTarget(
    context: Context,
    settings: CrashFileConfig = CrashFileConfig(),
) : LogTarget {

    private val writable = CrashFileWritable(CrashHeaderBuilder(context), settings)

    override fun log(type: L.LogType, tag: String?, msg: String?) = Unit

    override fun prepare(config: LoggerConfig) {
        writable.onInit(config)
        CrashHandler.install(writable)
    }

    override fun files(): List<File> {
        return writable.getFiles()
    }
}
