package com.tonapps.log.targets.crash

import com.tonapps.log.LoggerConfig
import com.tonapps.log.utils.FileManager
import com.tonapps.log.utils.LogHeaderBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class CrashFileWritable(
    private val builder: LogHeaderBuilder,
    private val settings: CrashFileConfig,
) {

    @Volatile
    private var crashDir: File? = null

    fun onInit(config: LoggerConfig) {
        crashDir = settings.outputDir(config.logsDir)
    }

    fun getFiles(): List<File> {
        val dir = crashDir ?: return emptyList()
        return files(dir)
    }

    fun write(thread: Thread, error: Throwable) {
        val dir = crashDir ?: return
        rotate(dir)

        val date = format().format(Date())
        val file = settings.outputLog(dir, date)

        val content = StringBuilder(buildHeader())
            .append("CRASH_DATE: ")
            .append(date)
            .append("\n")
            .append("CRASH_THREAD: ")
            .append(thread.name)
            .append("\n\n")
            .append(error.stackTraceToString())

        FileManager.createFile(file)
        FileManager.appendToFile(content, file)
    }

    private fun buildHeader(): String {
        return try {
            builder.build().toString()
        } catch (ignored: Throwable) {
            ""
        }
    }

    private fun rotate(dir: File) {
        val files = files(dir)
        if (files.size < settings.maxFilesCount) {
            return
        }

        files.drop(settings.maxFilesCount - 1).forEach { file ->
            FileManager.deleteFile(file)
        }
    }

    private fun files(dir: File): List<File> {
        return try {
            dir.listFiles()
                ?.filter { it.isFile }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        } catch (ignored: Throwable) {
            emptyList()
        }
    }

    private fun format(): SimpleDateFormat {
        return SimpleDateFormat("MM.dd.yyyy_HH.mm.ss.S", Locale.US)
    }
}
