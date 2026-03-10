package com.tonapps.log.targets.file.engine

import com.tonapps.log.LoggerConfig
import com.tonapps.log.utils.LogHeaderBuilder
import java.io.File
import java.io.FileOutputStream

data class LogFileConfig(
    val fileName: String = DEFAULT_LOG_FILENAME
) {
    companion object {
        private const val DEFAULT_LOG_FILENAME = "TK.log"
    }

    fun outputLog(root: File): File {
        return File(root, fileName)
    }
}

class CustomFileWritable(
    private val builder: LogHeaderBuilder,
    private val settings: LogFileConfig = LogFileConfig(),
) : FileWritable() {

    private lateinit var outputFile: File
    private var stream: FileOutputStream? = null

    override fun onInit(config: LoggerConfig) {
        synchronized(operationsLock) {
            outputFile = settings.outputLog(config.logsDir)
            createFile()
        }
    }

    override fun writeImpl(msg: String) {
        recreateIfRemoved()
        doWrite(msg)
    }

    override fun getFiles(): List<File> {
        return listOf(outputFile)
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
            }
        }
    }

    private fun doWrite(msg: String) {
        synchronized(operationsLock) {
            stream?.let { fileManager.writeStream(it, msg) }
        }
    }

    private fun recreateIfRemoved() {
        if (stream == null || !outputFile.exists()) { // Double check concurrency for speed up
            createFile()
        }
    }
}
