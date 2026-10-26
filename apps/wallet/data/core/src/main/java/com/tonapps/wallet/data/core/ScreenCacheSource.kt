package com.tonapps.wallet.data.core

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.extensions.cacheFolder
import com.tonapps.extensions.file
import com.tonapps.extensions.folder
import com.tonapps.extensions.packageInfo
import com.tonapps.extensions.toByteArray
import com.tonapps.extensions.toListParcel
import java.io.File
import java.io.IOException
import kotlin.math.min

class ScreenCacheSource(
    context: Context
) {

    private val rootFolder = context.cacheFolder("screen").let { screenFolder ->
        val currentInstallFolderName = context.packageInfo.lastUpdateTime.toString()
        screenFolder.listFiles()
            ?.filterNot { it.name == currentInstallFolderName }
            ?.forEach { it.deleteRecursively() }
        screenFolder.folder(currentInstallFolderName)
    }

    inline fun <reified T: Parcelable> get(
        name: String,
        walletId: String,
        block: (parcel: Parcel) -> T
    ): List<T> {
        val bytes = getData(name, walletId)
        if (bytes.isEmpty()) {
            return emptyList()
        }
        val l = bytes.toListParcel(block) ?: emptyList()
        return l
    }

    fun getData(
        name: String,
        walletId: String,
    ): ByteArray {
        try {
            val file = getFile(name, walletId)
            if (file == null || !file.exists() || file.length() == 0L || !file.canRead()) {
                return byteArrayOf()
            }
            return file.readBytes()
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            return byteArrayOf()
        }
    }

    fun set(
        name: String,
        walletId: String,
        list: List<Parcelable>
    ) {
        val file = getFile(name, walletId) ?: return
        if (list.isEmpty()) {
            file.delete()
        } else {
            val maxListSize = min(list.size, 25)
            val bytes = list.subList(0, maxListSize).toByteArray()
            // val bytes = list.toByteArray()
            try {
                file.writeBytes(bytes)
            } catch (e: IOException) {
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    private fun getFolder(name: String): File {
        return rootFolder.folder(name)
    }

    private fun getFile(name: String, walletId: String): File? {
        val folder = getFolder(name)
        val filename = "$walletId.dat"
        return folder.file(filename)
    }
}