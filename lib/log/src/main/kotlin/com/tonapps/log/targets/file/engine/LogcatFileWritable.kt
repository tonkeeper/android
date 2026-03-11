package com.tonapps.log.targets.file.engine

import com.tonapps.log.LoggerConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

data class LogcatSettings(
    val waitTime: Int = WAIT_TIME_MS,
    val bufferLines: Int = BUFFER_LINES,
    val bufferBytes: Int = BUFFER_BYTES,
    val maxFileSize: Int = MAX_FILE_SIZE
) {
    companion object {
        private const val WAIT_TIME_MS = 4 * 1000
        private const val BUFFER_LINES = 5000
        private const val BUFFER_BYTES = 128 * 1024
        private const val MAX_FILE_SIZE = 2 * 1024 * 1024
    }
}

internal class LogcatFileWritable(
    private val logcatSettings: LogcatSettings
) : FileWritable() {

    private val main: StringBuilder = StringBuilder(logcatSettings.bufferBytes)
    private val system: StringBuilder = StringBuilder(logcatSettings.bufferBytes)

    private var job: Job? = null

    private val isCollecting = AtomicBoolean(false)
    private val mutex = Mutex()

    private lateinit var fileMain: File
    private lateinit var fileSystem: File

    override fun writeImpl(msg: String) = Unit
    override fun getFiles(): List<File> = mutableListOf(fileMain, fileSystem)
    override fun canWrite(): Boolean = false

    override fun onInit(config: LoggerConfig) {
        if (isCollecting.compareAndSet(false, true)) {
            runBlocking {
                mutex.withLock {
                    fileMain = getFileByPath(config.logsDir, TYPE_MAIN)
                    fileSystem = getFileByPath(config.logsDir, TYPE_SYSTEM)

                    if (fileManager.recreateFile(fileMain) && fileManager.recreateFile(fileSystem)) {
                        job = config.scope.launch {
                            launchDumping()
                        }
                    }
                }
            }
        }
    }

    override fun onRelease() {
        if (isCollecting.compareAndSet(true, false)) {
            runBlocking {
                mutex.withLock {
                    try {
                        job?.cancel()
                    } catch (ignored: Exception) {}
                }
            }
        }
    }

    private suspend fun launchDumping() {
        try {
            clear()
            while(dump() && isCollecting.get()) {
                clear()
                delay(logcatSettings.waitTime.toLong())
            }
        } catch (ignored: Exception) { }
    }

    private suspend fun dump(): Boolean {
        return mutex.withLock {
            val hasMain = dumpIn(fileMain, main, TYPE_MAIN)
            val hasSystem = dumpIn(fileSystem, system, TYPE_SYSTEM)
            hasMain || hasSystem
        }
    }

    private fun dumpIn(file: File, from: StringBuilder, type: String): Boolean {
        val isReadable = file.length() < logcatSettings.maxFileSize

        if (isReadable) {
            collect(from, type)
            fileManager.appendToFile(from, file)
        }

        return isReadable
    }

    private fun clear() {
        exec(null, arrayOf("logcat", "-c"))
        main.setLength(0)
        system.setLength(0)
    }

    private fun collect(builder: StringBuilder, type: String) {
        builder.append("\n")
        val args = arrayOf("logcat", "-t", logcatSettings.bufferLines.toString(), "-b", type, "-v", "time", "brief")
        exec(builder, args)
    }

    private fun exec(builder: StringBuilder?, args: Array<String>) {
        var process: Process? = null
        try {
            process = ProcessBuilder().command(*args)
                .redirectErrorStream(true)
                .start()

            BufferedReader(InputStreamReader(process.inputStream), logcatSettings.bufferBytes)
                .use { reader ->
                    builder?.append(reader.readText())
                }
        } catch (ignored: Exception) {
        } finally {
            process?.destroy()
        }
    }

    private fun getFileByPath(logPath: File, type: String): File {
        return File(logPath, "$type.log")
    }

    companion object {
        private const val TYPE_MAIN = "main"
        private const val TYPE_SYSTEM = "system"
    }
}
