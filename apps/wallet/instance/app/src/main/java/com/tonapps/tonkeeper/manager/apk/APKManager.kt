package com.tonapps.tonkeeper.manager.apk

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.Parcelable
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.core.flags.RemoteConfig
import com.tonapps.extensions.appVersionName
import com.tonapps.extensions.file
import com.tonapps.extensions.folder
import com.tonapps.tonkeeper.extensions.safeCanRequestPackageInstalls
import com.tonapps.tonkeeper.worker.ApkDownloadWorker
import com.tonapps.tonkeeperx.BuildConfig
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.ApkEntity
import com.tonapps.wallet.api.entity.AppVersion
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.io.File
import java.io.IOException
import java.util.UUID

class APKManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val environment: com.tonapps.tonkeeper.Environment,
    private val api: API,
    private val remoteConfig: RemoteConfig,
    private val settingsRepository: SettingsRepository,
) {

    @Parcelize
    data class HideReminder(
        val timestamp: Long = 0,
        val count: Int = 0,
    ): Parcelable

    sealed class Status {
        data object Default : Status()
        data class UpdateAvailable(val apk: ApkEntity) : Status()
        data class Downloading(val progress: Int, val apk: ApkEntity) : Status()
        data class Downloaded(val apk: ApkEntity, val file: File) : Status()
        data class Failed(val apk: ApkEntity) : Status()
    }

    private val _statusFlow = MutableStateFlow<Status>(Status.Default)
    val statusFlow = _statusFlow.asStateFlow()

    private val folder: File by lazy {
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!.folder("apk")
    }

    init {
        api.configFlow.map { it.apk }
            .filterNotNull()
            .onEach(::checkUpdates)
            .flowOn(Dispatchers.IO)
            .launchIn(scope)
    }

    private fun getHideReminder(): HideReminder {
        val timestamp = settingsRepository.prefs.getLong(UPDATE_REMINDER_TIMESTAMP_KEY, 0)
        val count = settingsRepository.prefs.getInt(UPDATE_REMINDER_COUNT_KEY, 0)
        return HideReminder(
            timestamp = timestamp,
            count = count
        )
    }

    fun closeReminder() {
        val oldState = getHideReminder()
        val newState = oldState.copy(
            timestamp = System.currentTimeMillis(),
            count = oldState.count + 1
        )

        settingsRepository.prefs.edit {
            putLong(UPDATE_REMINDER_TIMESTAMP_KEY, newState.timestamp)
            putInt(UPDATE_REMINDER_COUNT_KEY, newState.count)
        }
    }

    private fun isShowReminder(): Boolean {
        val state = getHideReminder()
        if (0 >= state.count) {
            return true
        }
        val days = if (state.count > 2) 7 else 1

        val currentTime = System.currentTimeMillis()
        val lastTime = state.timestamp

        val diff = currentTime - lastTime
        val daysDiff = diff / (1000 * 60 * 60 * 24)
        return daysDiff >= days
    }

    private fun getFile(apk: ApkEntity): File? {
        return folder.file("Tonkeeper_${apk.apkName}.apk")
    }

    private fun checkUpdates(apk: ApkEntity) {
        if (BuildConfig.DEBUG || environment.isFromGooglePlay || !remoteConfig.inAppUpdateAvailable || !isShowReminder()) {
            return
        }


        val currentVersion = AppVersion(context.appVersionName)
        if (currentVersion.integer >= apk.apkName.integer) {
            return
        }
        val file = getFile(apk)
        if (file != null && file.exists() && file.length() > 0) {
            _statusFlow.value = Status.Downloaded(apk, file)
        } else {
            _statusFlow.value = Status.UpdateAvailable(apk)
        }
    }

    fun download(apk: ApkEntity) {
        val file = getFile(apk)
        if (file == null) {
            _statusFlow.value = Status.Failed(apk)
            return
        }
        scope.launch(Dispatchers.IO) { cleanupApkFiles(keep = file) }
        val workerId = ApkDownloadWorker.start(context, apk.apkDownloadUrl, file.path)
        ApkDownloadWorker.flowProgress(context, workerId).onEach {
            if (it >= 100) {
                _statusFlow.value = Status.Downloaded(apk, file)
            } else if (it < 0) {
                _statusFlow.value = Status.Failed(apk)
            } else {
                _statusFlow.value = Status.Downloading(it, apk)
            }
        }.launchIn(scope)

        _statusFlow.value = Status.Downloading(0, apk)
    }

    fun install(context: Context, file: File): Boolean {
        val installFile = getValidFile(file)
        if (installFile == null || environment.isFromGooglePlay) {
            return false
        }

        scope.launch(Dispatchers.Main) {
            if (!context.safeCanRequestPackageInstalls()) {
                openSettings()
            } else {
                val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", installFile)
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, "application/vnd.android.package-archive")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(intent)
                clearInstallRequest(context)
                // Installer actually launched — now the install notification has served its
                // purpose and can be dismissed (it is kept alive on the openSettings() branch).
                NotificationManagerCompat.from(context).cancel(ApkDownloadWorker.INSTALL_NOTIFICATION_ID)
            }
        }
        return true
    }

    @SuppressLint("InlinedApi")
    private fun openSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun installDownloaded(context: Context, token: String?): Boolean {
        if (token.isNullOrBlank()) {
            return false
        }

        val prefs = getInstallPrefs(context)
        if (prefs.getString(INSTALL_TOKEN_KEY, null) != token) {
            return false
        }

        val file = prefs.getString(INSTALL_FILE_KEY, null)?.let(::File) ?: return false
        return install(context, file)
    }

    private fun cleanupApkFiles(keep: File) {
        try {
            val keepPath = keep.canonicalFile.path
            cleanApkFolder(keepPath)
            cleanLegacyApkFolder()
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun cleanApkFolder(keepPath: String) {
        folder.listFiles()?.forEach { file ->
            if (
                file.isFile &&
                file.extension.equals("apk", ignoreCase = true) &&
                file.canonicalFile.path != keepPath
            ) {
                file.delete()
            }
        }
    }

    private fun cleanLegacyApkFolder() {
        folder.parentFile?.listFiles()?.forEach { file ->
            if (
                file.isFile &&
                file.name.startsWith("Tonkeeper_") &&
                file.extension.equals("apk", ignoreCase = true)
            ) {
                file.delete()
            }
        }
    }

    private fun getValidFile(file: File): File? {
        return try {
            val canonicalFile = file.canonicalFile
            val canonicalFolder = folder.canonicalFile

            if (
                canonicalFile.isFile &&
                canonicalFile.extension.equals("apk", ignoreCase = true) &&
                canonicalFile.path.startsWith(canonicalFolder.path + File.separator)
            ) {
                canonicalFile
            } else {
                null
            }
        } catch (e: IOException) {
            null
        }
    }

    companion object {
        private const val UPDATE_REMINDER_TIMESTAMP_KEY = "apk_update_reminder_timestamp"
        private const val UPDATE_REMINDER_COUNT_KEY = "apk_update_reminder_count"

        private const val INSTALL_PREFS = "apk_install"
        private const val INSTALL_TOKEN_KEY = "install_token"
        private const val INSTALL_FILE_KEY = "install_file"

        /**
         * Stores the APK path in private prefs and returns a random token instead of putting the
         * path into the PendingIntent. This prevents external apps from injecting an arbitrary file
         * path to install. The token only authorizes the install-notification tap and is used to
         * fetch our actual downloaded APK path back from prefs.
         */
        fun saveInstallRequest(context: Context, file: File): String {
            val token = UUID.randomUUID().toString()
            getInstallPrefs(context).edit {
                putString(INSTALL_TOKEN_KEY, token)
                putString(INSTALL_FILE_KEY, file.absolutePath)
            }
            return token
        }

        fun clearInstallRequest(context: Context) {
            getInstallPrefs(context).edit {
                remove(INSTALL_TOKEN_KEY)
                remove(INSTALL_FILE_KEY)
            }
        }

        private fun getInstallPrefs(context: Context) = context.getSharedPreferences(INSTALL_PREFS, Context.MODE_PRIVATE)
    }
}
