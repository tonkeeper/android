package com.tonapps.log.utils

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

internal object FileManager {

    fun writeStream(stream: OutputStream, msg: String) {
        try {
            stream.write(msg.toByteArray(charset("UTF-8")))
            stream.flush()
        } catch (ignored: Throwable) {}
    }

    fun openStream(file: File, append: Boolean = true): FileOutputStream? {
        var stream: FileOutputStream? = null
        try {
            stream = FileOutputStream(file, append)
        } catch (ignored: Throwable) { }
        return stream
    }

    fun appendToFile(builder: StringBuilder, file: File) {
        try {
            val bytes = builder.toString().toByteArray()
            write(file, bytes, true)
        } catch (ignored: Throwable) {}
    }

    fun deleteFile(file: File): Boolean {
        if (!file.exists()) {
            return false
        }

        return try {
            if (file.isDirectory) deleteDirectory(file) else file.delete()
        } catch (ignored: Throwable) {
            return false
        }
    }

    fun recreateFile(file: File): Boolean {
        try {
            if (file.exists()) {
                file.delete()
            }
        } catch (ignored: Throwable) {}

        return createFile(file)
    }

    fun createFile(file: File): Boolean {
        try {
            val parentFile = file.parentFile
            if (parentFile?.exists() == false) {
                parentFile.mkdirs()
            }

            return file.createNewFile()
        } catch (ignored: Throwable) {}
        return false
    }

    @Throws(Exception::class)
    fun write(file: File, data: ByteArray, append: Boolean) {
        FileOutputStream(file, append)
            .use { it.write(data) }
    }

    fun closeAndFlush(stream: OutputStream?) {
        try {
            stream?.flush()
        } catch (ignore: Exception) {
        }

        close(stream)
    }

    fun close(stream: Closeable?) {
        try {
            stream?.close()
        } catch (ignore: Exception) {
        }
    }

    private fun deleteDirectory(directory: File): Boolean {
        if (directory.exists()) {
            val files = directory.listFiles()
            if (files != null) {
                for (i in files.indices) {
                    if (files[i].isDirectory) {
                        deleteDirectory(files[i])
                    } else {
                        files[i].delete()
                    }
                }
            }
        }
        return directory.delete()
    }
}
