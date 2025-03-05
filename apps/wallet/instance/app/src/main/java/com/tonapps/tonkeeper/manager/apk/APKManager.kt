package com.tonapps.tonkeeper.manager.apk

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.Parcelable
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.tonapps.extensions.appVersionName
import com.tonapps.extensions.file
import com.tonapps.tonkeeper.extensions.safeCanRequestPackageInstalls
import com.tonapps.tonkeeper.worker.ApkDownloadWorker
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.ApkEntity
import com.tonapps.wallet.api.entity.AppVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.Parcelize
import java.io.File

class APKManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val environment: com.tonapps.tonkeeper.Environment,
    private val api: API,
) {

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
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
    }

    init {
        api.configFlow.map { it.apk }
            .filterNotNull()
            .onEach(::checkUpdates)
            .flowOn(Dispatchers.IO)
            .launchIn(scope)
    }

    private fun getFile(apk: ApkEntity): File {
        return folder.file("Tonkeeper_${apk.apkName}.apk")
    }

    private fun checkUpdates(apk: ApkEntity) {
        if (environment.isFromGooglePlay) {
            return
        }
        val currentVersion = AppVersion(context.appVersionName)
        if (currentVersion.integer >= apk.apkName.integer) {
            return
        }
        val file = getFile(apk)
        if (file.exists() && file.length() > 0) {
            _statusFlow.value = Status.Downloaded(apk, file)
        } else {
            _statusFlow.value = Status.UpdateAvailable(apk)
        }
    }

    fun download(apk: ApkEntity) {
        val file = getFile(apk)
        val workerId = ApkDownloadWorker.start(context, apk.apkDownloadUrl, file.path)
        ApkDownloadWorker.flowProgress(context, workerId).onEach {
            if (it >= 100) {
                _statusFlow.value = Status.Downloaded(apk, file)
            } else {
                _statusFlow.value = Status.Downloading(it, apk)
            }
        }.launchIn(scope)

        _statusFlow.value = Status.Downloading(0, apk)
    }

    fun install(context: Context, file: File): Boolean {
        if (!isValidFile(file) || environment.isFromGooglePlay) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.safeCanRequestPackageInstalls()) {
            openSettings()
        } else {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
        }
        return true
    }

    @SuppressLint("InlinedApi")
    private fun openSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun isValidFile(file: File) = file.path.startsWith(folder.path)
}