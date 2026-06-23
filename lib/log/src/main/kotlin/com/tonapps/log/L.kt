package com.tonapps.log

import android.content.Context
import android.util.Log
import com.tonapps.lib.log.BuildConfig
import com.tonapps.log.targets.console.ConsoleLogTarget
import com.tonapps.log.targets.file.FileLogTarget
import com.tonapps.log.targets.file.engine.CustomFileWritable
import com.tonapps.log.targets.file.engine.LogcatFileWritable
import com.tonapps.log.targets.file.engine.LogcatSettings
import com.tonapps.log.utils.FileManager
import com.tonapps.log.utils.LogArchiverEncoder
import com.tonapps.log.utils.LogHeaderBuilder
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

object L {

    @Suppress("EnumEntryName")
    enum class LogType {
        v, d, i, w, e;

        fun toLog(): Int {
            return when (this) {
                v -> Log.VERBOSE
                d -> Log.DEBUG
                i -> Log.INFO
                w -> Log.WARN
                e -> Log.ERROR
            }
        }
    }

    @Volatile
    private lateinit var config: LoggerConfig

    @Volatile
    private lateinit var targets: List<LogTarget>

    private val isInit = AtomicBoolean(false)

    private val lock = ReentrantReadWriteLock()

    private val logStringBuilder by lazy {
        StringBuilder()
    }

    fun initialize(config: LoggerConfig, targets: List<LogTarget>) {
        lock.write {
            if (isInit.compareAndSet(false, true)) {
                this.config = config
                this.targets = targets

                targets.forEach { it.prepare(config) }
            }
        }
    }

    fun setTargets(newTargets: List<LogTarget>) {
        if (!isInit.get()) {
            return
        }

        config.executor.execute {
            lock.write {
                targets.forEach { it.release() }
                targets = newTargets
                targets.forEach { it.prepare(config) }
            }
        }
    }

    fun hasTargets(): Boolean {
        return try {
            targets.isNotEmpty()
        } catch (e: Throwable) {
            false
        }
    }

    fun hasLogs(): Boolean {
        if (!isInit.get()) {
            return false
        }

        return targets.map { it.files() }
            .flatten()
            .isNotEmpty()
    }

    fun capture(onComplete: (File?) -> Unit) {
        if (!isInit.get()) {
            return
        }

        config.executor.execute {
            lock.write {
                val files = targets.map { it.files() }
                    .flatten()

                val pubKey = config.pubKeyProvider.invoke()
                val outputPub = config.outputPublicArchive()

                val archiver = LogArchiverEncoder(
                    pubKey = pubKey,
                    outputPub = outputPub,
                    output = config.outputArchive(),
                )

                if (!archiver.archive(files)) {
                    onComplete(null)
                    return@write
                }

                files.forEach { file ->
                    FileManager.deleteFile(file)
                }

                onComplete(outputPub)
            }
        }
    }

    fun defaultTargets(context: Context, isLogsEnabled: Boolean): List<LogTarget> {
        return buildList {
            if (BuildConfig.DEBUG) {
                add(ConsoleLogTarget())
            }

            if (isLogsEnabled) {
                add(FileLogTarget(CustomFileWritable(LogHeaderBuilder.Default(context))))
                add(FileLogTarget(LogcatFileWritable(LogcatSettings())))
            }
        }
    }

    fun v(e: Throwable, vararg o: Any) = logEx(LogType.v, e, *o)
    fun v(vararg o: Any) = log(LogType.v, *o)

    fun d(e: Throwable, vararg o: Any) = logEx(LogType.d, e, *o)
    fun d(vararg o: Any) = log(LogType.d, *o)

    fun i(e: Throwable, vararg o: Any) = logEx(LogType.i, e, *o)
    fun i(vararg o: Any) = log(LogType.i, *o)

    fun w(e: Throwable, vararg o: Any) = logEx(LogType.w, e, *o)
    fun w(vararg o: Any) = log(LogType.w, *o)

    fun e(e: Throwable, vararg o: Any) = logEx(LogType.e, e, *o)
    fun e(vararg o: Any) = log(LogType.e, *o)
    fun e(e: Throwable) = logEx(LogType.e, e)

    private fun log(logType: LogType, vararg o: Any) {
        logEx(logType, null, *o)
    }

    private fun logEx(
        logType: LogType,
        e: Throwable?,
        vararg o: Any,
        loggerClassName: String = L::class.java.name
    ) {
        if (!isInit.get()) {
            val msg = o.joinToString(" | ") { it.toString() }
            Log.println(
                logType.toLog(),
                "L", "Log before init L!"
                    + "\nMessage: " + msg
                    + "\nError: " + e?.stackTraceToString()
            )
            return
        }

        if (targets.isEmpty()) {
            return
        }

        val thread = Thread.currentThread()
        val element = trace(thread, loggerClassName)
        val className = if (element != null) {
            element.className
        } else {
            loggerClassName
        }

        config.executor.execute {
            lock.read {
                logExSync(
                    logType = logType,
                    e = e,
                    className = className,
                    o = o,
                    threadName = thread.name,
                    methodName = element?.methodName ?: "unknown",
                    lineNumber = element?.lineNumber ?: 0
                )
            }
        }
    }

    private fun logExSync(
        logType: LogType,
        e: Throwable?,
        className: String,
        threadName: String,
        methodName: String,
        lineNumber: Int,
        vararg o: Any,
    ) {
        logStringBuilder
            .clear()
            .append("[$threadName] $methodName:$lineNumber ")

        for (obj in o) {
            val maxLength = config.maxLength
            val data = if (obj is CharSequence && obj.length > maxLength) {
                obj.substring(0, maxLength)
            } else {
                obj
            }
            logStringBuilder.append(data).append(" ")

            if (logStringBuilder.length >= maxLength) {
                // strip long input data.
                logStringBuilder.append(" ...(strip long data, more then $maxLength bytes) ")
                break
            }
        }

        val substringWithSimpleClassName = className.substringAfterLast(".")

        var tag = className
        if (substringWithSimpleClassName != className) {
            tag = substringWithSimpleClassName
        }

        val msg = logStringBuilder.toString()
        if (e == null) {
            targets.forEach { target ->
                target.log(logType, tag, msg)
            }
        } else {
            targets.forEach { target ->
                target.log(logType, tag, msg, e)
            }
        }
    }

    private fun trace(thread: Thread, className: String): StackTraceElement? {
        val e = thread.stackTrace
        var found = false
        for (s in e) {
            if (s.className == className) {
                found = true
            }
            if (found && s.className != className) {
                return s
            }
        }
        return null
    }
}
