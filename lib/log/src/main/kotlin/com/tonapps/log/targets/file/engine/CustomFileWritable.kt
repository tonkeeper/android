package com.tonapps.log.targets.file.engine

import com.tonapps.log.LoggerConfig
import com.tonapps.log.utils.LogHeaderBuilder
import java.io.File
import java.io.FileOutputStream

data class LogFileConfig(
    val fileName: String = DEFAULT_LOG_FILENAME,
    val maxFileSizeBytes: Long = DEFAULT_MAX_FILE_SIZE,
    val maxRotationCount: Int = DEFAULT_MAX_ROTATION_COUNT,
) {
    companion object {
        private const val DEFAULT_LOG_FILENAME = "TK.log"
        private const val DEFAULT_MAX_FILE_SIZE = 5L * 1024 * 1024
        private const val DEFAULT_MAX_ROTATION_COUNT = 1
    }

    fun outputLog(root: File): File {
        return File(root, fileName)
    }

    fun backupLog(root: File, index: Int): File {
        val name = fileName.substringBeforeLast('.')
        val extension = fileName.substringAfterLast('.')
        return File(root, "$name.$index.$extension")
    }
}

class CustomFileWritable(
    private val builder: LogHeaderBuilder,
    private val settings: LogFileConfig = LogFileConfig(),
) : FileWritable() {

    private lateinit var outputFile: File
    private lateinit var logsDir: File
    private var stream: FileOutputStream? = null
    private var bytesWritten: Long = 0

    override fun onInit(config: LoggerConfig) {
        synchronized(operationsLock) {
            logsDir = config.logsDir
            outputFile = settings.outputLog(config.logsDir)
            createFile()
        }
    }

    override fun writeImpl(msg: String) {
        recreateIfRemoved()
        if (bytesWritten >= settings.maxFileSizeBytes) {
            rotate()
        }
        doWrite(msg)
    }

    override fun getFiles(): List<File> {
        return buildList {
            add(outputFile)
            for (index in 1..settings.maxRotationCount) {
                settings.backupLog(logsDir, index).takeIf { it.exists() }?.let(::add)
            }
        }
    }

    override fun canWrite(): Boolean {
        return stream != null
    }

    override fun onRelease() {
        fileManager.closeAndFlush(stream)
    }

    private fun createFile() {
        synchronized(operationsLock) {
            if (stream == null || !outputFile.exists()) {

                fileManager.createFile(outputFile)
                fileManager.appendToFile(builder.build(), outputFile)

                stream?.let { fileManager.closeAndFlush(it) }
                stream = fileManager.openStream(outputFile)
                bytesWritten = outputFile.length()
            }
        }
    }

    private fun rotate() {
        synchronized(operationsLock) {
            fileManager.closeAndFlush(stream)
            stream = null

            for (index in settings.maxRotationCount downTo 1) {
                val destination = settings.backupLog(logsDir, index)
                val source = if (index == 1) outputFile else settings.backupLog(logsDir, index - 1)
                fileManager.deleteFile(destination)
                if (source.exists() && !source.renameTo(destination)) {
                    fileManager.deleteFile(source)
                }
            }

            createFile()
        }
    }

    private fun doWrite(msg: String) {
        synchronized(operationsLock) {
            stream?.let {
                fileManager.writeStream(it, msg)
                bytesWritten += msg.toByteArray(Charsets.UTF_8).size
            }
        }
    }

    private fun recreateIfRemoved() {
        if (stream == null || !outputFile.exists()) { // Double check concurrency for speed up
            createFile()
        }
    }
}
