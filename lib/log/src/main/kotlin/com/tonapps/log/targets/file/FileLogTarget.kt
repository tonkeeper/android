package com.tonapps.log.targets.file

import com.tonapps.log.L
import com.tonapps.log.LogTarget
import com.tonapps.log.LoggerConfig
import com.tonapps.log.targets.file.engine.FileWritable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

open class FileLogTarget(
    private val writer: FileWritable
) : LogTarget {

    private val newLineRegex = "\n".toRegex()

    private val format: ThreadLocal<SimpleDateFormat> = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("MM.dd.yyyy hh:mm:ss.S", Locale.getDefault())
        }
    }

    private val stringBuilder: ThreadLocal<StringBuilder> = object : ThreadLocal<StringBuilder>() {
        override fun initialValue(): StringBuilder {
            return StringBuilder()
        }
    }

    private val isInit = AtomicBoolean(false)

    override fun prepare(config: LoggerConfig) {
        if (isInit.compareAndSet(false, true)) {
            writer.onInit(config)
        }
    }

    override fun release() {
        if (isInit.compareAndSet(true, false)) {
            writer.onRelease()
        }
    }

    override fun log(type: L.LogType, tag: String?, msg: String?) {
        logToFile(type, tag, msg)
    }

    override fun files(): List<File> {
        return writer.getFiles()
    }

    private fun logToFile(type: L.LogType, tag: String?, msg: String?) {
        try {
            if (!writer.canWrite()) {
                return
            }

            val format = format.get()!! // Java code without annotation
            val result = stringBuilder.get()!! // Java code without annotation
            val level = toLevel(type)

            result.clear()

            msg?.let {
                result.append(format.format(Date()))
                result.append("\t$level").append("\t$tag")

                writer.apply {
                    write(result.toString())
                    write(msg)
                    write("\n")
                }
            }
        } catch (ignored: Exception) {}
    }

    private fun toLevel(type: L.LogType): String {
        return when (type) {
            L.LogType.d -> "D"
            L.LogType.v -> "V"
            L.LogType.i -> "I"
            L.LogType.w -> "W"
            L.LogType.e -> "E"
        }
    }
}
