package com.tonapps.wallet.data.core

import android.content.Context
import android.util.Log
import com.tonapps.extensions.cacheFolder
import com.tonapps.extensions.file
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

abstract class BlobDataSource<D>(
    context: Context,
    path: String,
    lruInitialCapacity: Int = 5,
    private val timeout: Long = TimeUnit.DAYS.toMillis(90)
) {

    private val lruCache = ConcurrentHashMap<String, D>(lruInitialCapacity, 1.0f, 2)
    private val diskFolder = context.cacheFolder(path)

    fun getCache(key: String): D? {
        return getLruCache(key) ?: getDiskCache(key)
    }

    fun setCache(key: String, value: D) {
        setDiskCache(key, value)
    }

    fun clearCache(key: String) {
        lruCache.remove(key)
        diskFile(key).delete()
    }

    private fun getLruCache(key: String): D? {
        return lruCache[key]
    }

    private fun setLruCache(key: String, value: D) {
        lruCache[key] = value
    }

    private fun getDiskCache(key: String): D? {
        val bytes = readDiskCache(key) ?: return null
        val value = onUnmarshall(bytes) ?: return null
        setLruCache(key, value)
        return value
    }

    private fun setDiskCache(key: String, value: D) {
        val file = diskFile(key)
        val bytes = onMarshall(value)
        file.writeBytes(bytes)
        setLruCache(key, value)
    }

    private fun diskFile(key: String): File {
        return diskFolder.file("${key}.dat")
    }

    private fun readDiskCache(key: String): ByteArray? {
        val file = diskFile(key)
        val lastModified = file.lastModified()
        if (file.length() == 0L || 0L >= lastModified) {
            return null
        }
        val diff = System.currentTimeMillis() - lastModified
        if (diff > timeout) {
            file.delete()
            return null
        }

        return try {
            val bytes = file.readBytes()
            if (bytes.isEmpty()) null else bytes
        } catch (e: IOException) {
            null
        }
    }

    abstract fun onMarshall(data: D): ByteArray

    abstract fun onUnmarshall(bytes: ByteArray): D?

}