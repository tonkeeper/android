package com.tonapps.log.targets.file.engine

import com.tonapps.log.L
import com.tonapps.log.LoggerConfig
import com.tonapps.log.utils.FileManager
import java.io.File

abstract class FileWritable {

    internal val fileManager = FileManager
    protected val operationsLock = Any()

    fun write(msg: String) {
        try {
            writeImpl(msg)
        } catch (th: Throwable) {
            L.e("FileWritable", "File writable error", th)
        }
    }

    protected abstract fun writeImpl(msg: String)

    abstract fun onInit(config: LoggerConfig)
    abstract fun onRelease()

    abstract fun getFiles(): List<File>
    abstract fun canWrite(): Boolean
}
