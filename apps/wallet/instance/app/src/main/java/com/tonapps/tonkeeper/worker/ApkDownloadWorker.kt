package com.tonapps.tonkeeper.worker

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.extensions.file
import com.tonapps.tonkeeper.extensions.workManager
import com.tonapps.tonkeeper.helper.NotificationsHelper
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.FileDownloader
import com.tonapps.wallet.api.FileDownloader.DownloadStatus
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

class ApkDownloadWorker(
    private val context: Context,
    workParam: WorkerParameters,
    api: API,
): CoroutineWorker(context, workParam) {

    private val fileDownloader = FileDownloader(api.defaultHttpClient)

    private val notificationManager: NotificationManagerCompat by lazy {
        NotificationsHelper.createNotificationManager(context)
    }

    override suspend fun doWork(): Result {
        val downloadUrl = inputData.getString(ARG_URL) ?: return Result.failure()
        val targetFile = inputData.getString(ARG_FILE)?.let {
            File(it)
        } ?: return Result.failure()
        setForeground(getForegroundInfo())
        return if (downloadSync(downloadUrl, targetFile) is DownloadStatus.Success) {
            showInstallNotification(targetFile)
            Result.success()
        } else {
            Result.failure()
        }
    }

    private suspend fun downloadSync(
        url: String,
        file: File,
    ) = suspendCancellableCoroutine { continuation ->
        download(url, file) { status ->
            if (!continuation.isCompleted) {
                continuation.resume(status)
            }
        }
    }

    private fun download(
        url: String,
        file: File,
        result: (DownloadStatus) -> Unit
    ) {
        var lastUpdateTime = 0L
        fileDownloader.download(
            url = url,
            outputFile = file,
            callback = { status ->
                if (status is DownloadStatus.Progress) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateTime >= 320) {
                        updateNotification(status)
                        lastUpdateTime = currentTime
                    }
                } else {
                    result(status)
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun showInstallNotification(file: File) {
        val installUri = Uri.parse("tonkeeper://install")
            .buildUpon()
            .appendQueryParameter("file", file.absolutePath)
            .build()

        val builder = NotificationCompat.Builder(context, NAME)
            .setContentTitle(getString(Localization.download_completed))
            .setContentText(getString(Localization.tap_to_install))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(NotificationsHelper.getPendingIntent(context, installUri))
        val notification = builder.build()
        notificationManager.notify(4342, notification)
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(status: DownloadStatus.Progress) {
        try {
            setProgressAsync(workDataOf(ARG_PROGRESS to status.percent))
            val builder = notificationBuilder()
                .setProgress(100, status.percent, false)
                .setContentText(status.downloadSpeed)
            val notification = builder.build()
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun notificationBuilder(): NotificationCompat.Builder {
        NotificationsHelper.getOrCreateChannel(context, NAME)
        val contentIntent = NotificationsHelper.getPendingIntent(context, "tonkeeper://wallet")
        return NotificationCompat.Builder(context, NAME)
            .setContentTitle(getString(Localization.downloading_app_update))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(android.R.drawable.ic_delete, getString(android.R.string.cancel), WorkManager.getInstance(context).createCancelPendingIntent(id))
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val builder = notificationBuilder()
            .setContentText(getString(Localization.preparing_for_downloading))
            .setProgress(100, 0, true)
        val notification = builder.build()
        return createForegroundInfo(notification)
    }

    private fun createForegroundInfo(notification: Notification): ForegroundInfo {
        return if (android.os.Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun getString(id: Int): String {
        return context.getString(id)
    }

    companion object {

        private const val NOTIFICATION_ID = 9244
        private const val NAME = "apk_download"

        private const val ARG_URL = "url"
        private const val ARG_FILE = "file"
        private const val ARG_PROGRESS = "progress"

        fun start(
            context: Context,
            downloadUrl: String,
            targetFile: String
        ): Operation {
            val inputData = Data.Builder()
                .putString(ARG_URL, downloadUrl)
                .putString(ARG_FILE, targetFile)
                .build()
            val builder = OneTimeWorkRequestBuilder<ApkDownloadWorker>()
            builder.requiredNetwork()
            builder.setInputData(inputData)
            builder.addTag(NAME)
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            return context.workManager.enqueue(builder.build())
        }

        fun flowProgress(context: Context): Flow<Int> {
            return context.workManager.getWorkInfosByTagFlow(NAME).mapNotNull {
                it.firstOrNull()
            }.map { it.progress.getInt(ARG_PROGRESS, 0) }
        }

    }

}