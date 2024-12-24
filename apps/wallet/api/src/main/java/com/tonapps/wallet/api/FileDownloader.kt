package com.tonapps.wallet.api

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.roundToInt

class FileDownloader(private val okHttpClient: OkHttpClient) {

    sealed class DownloadStatus {
        data class Progress(
            val downloadedBytes: Long,
            val totalBytes: Long,
            val percent: Int,
            val downloadSpeed: String
        ) : DownloadStatus()

        data class Error(val throwable: Throwable) : DownloadStatus()
        data class Success(val file: File) : DownloadStatus()
    }

    fun download(
        url: String,
        outputFile: File,
        bufferSize: Int = DEFAULT_BUFFER_SIZE,
        callback: (DownloadStatus) -> Unit
    ) {
        try {
            val request = Request.Builder()
                .url(url)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected response code: ${response.code}")
                }

                val body = response.body ?: throw IOException("Response body is null")
                val contentLength = body.contentLength()
                var downloadedBytes = 0L
                var lastEmitTime = System.currentTimeMillis()
                var bytesFromLastEmit = 0L

                FileOutputStream(outputFile).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(bufferSize)
                        var bytes = input.read(buffer)

                        while (bytes >= 0) {
                            output.write(buffer, 0, bytes)
                            downloadedBytes += bytes
                            bytesFromLastEmit += bytes

                            val currentTime = System.currentTimeMillis()
                            val timeElapsed = currentTime - lastEmitTime
                            if (timeElapsed >= 100) {
                                val speedBytesPerSec = (bytesFromLastEmit * 1000.0 / timeElapsed).roundToInt()
                                val progress = DownloadStatus.Progress(
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = contentLength,
                                    percent = if (contentLength > 0) {
                                        (downloadedBytes * 100 / contentLength).toInt()
                                    } else 0,
                                    downloadSpeed = formatSpeed(speedBytesPerSec)
                                )
                                callback(progress)

                                lastEmitTime = currentTime
                                bytesFromLastEmit = 0
                            }
                            bytes = input.read(buffer)
                        }
                    }
                }
                callback(DownloadStatus.Success(outputFile))
            }
        } catch (e: Exception) {
            callback(DownloadStatus.Error(e))
            outputFile.delete()
        }
    }

    private fun formatSpeed(bytesPerSec: Int): String {
        return when {
            bytesPerSec >= 1_000_000 -> "%.1f MB/s".format(bytesPerSec / 1_000_000.0)
            bytesPerSec >= 1_000 -> "%.1f KB/s".format(bytesPerSec / 1_000.0)
            else -> "$bytesPerSec B/s"
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 8192 // 8KB buffer
    }
}
