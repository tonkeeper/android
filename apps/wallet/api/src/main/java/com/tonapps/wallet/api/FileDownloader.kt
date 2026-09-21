package com.tonapps.wallet.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

class FileDownloader(private val okHttpClient: OkHttpClient) {

    sealed class DownloadStatus {
        data class Progress(
            val downloadedBytes: Long = 0,
            val totalBytes: Long = 0,
            val percent: Int = 0,
            val downloadSpeed: String = ""
        ) : DownloadStatus()

        data class Error(val throwable: Throwable) : DownloadStatus()

        data class Success(val file: File) : DownloadStatus()
    }

    fun download(
        url: String,
        outputFile: File,
        bufferSize: Int = DEFAULT_BUFFER_SIZE
    ): Flow<DownloadStatus> = flow {
        var connection: HttpURLConnection? = null

        // Compute the terminal status inside the try/catch, but emit it afterwards: emitting from
        // within a try that swallows exceptions would violate flow exception transparency (a
        // downstream cancellation surfaces as an exception here). Progress is emitted in-place; the
        // terminal event is emitted last so conflate() (below) still delivers it.
        val terminal: DownloadStatus = try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP error code: $responseCode")
            }

            val contentLength = connection.contentLengthLong
            var downloadedBytes = 0L
            var lastEmitTime = System.currentTimeMillis()
            var bytesFromLastEmit = 0L

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
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
                            emit(
                                DownloadStatus.Progress(
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = contentLength,
                                    percent = if (contentLength > 0) {
                                        (downloadedBytes * 100 / contentLength).toInt()
                                    } else {
                                        0
                                    },
                                    downloadSpeed = formatSpeed(speedBytesPerSec)
                                )
                            )

                            lastEmitTime = currentTime
                            bytesFromLastEmit = 0
                        }

                        bytes = input.read(buffer)
                    }
                }
            }

            DownloadStatus.Success(outputFile)
        } catch (e: CancellationException) {
            outputFile.delete()
            throw e
        } catch (e: Exception) {
            outputFile.delete()
            DownloadStatus.Error(e)
        } finally {
            connection?.disconnect()
        }

        emit(terminal)
        // conflate() so a slow collector drops intermediate progress percentages instead of
        // back-pressuring the download loop (we don't need every %). The terminal event is emitted
        // last, so conflation still delivers it — only the terminal event needs a guarantee.
    }.conflate().flowOn(Dispatchers.IO)

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
