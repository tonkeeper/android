package com.tonapps.extensions

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

fun File.folder(name: String): File {
    val folder = File(this, name)
    if (!folder.exists()) {
        folder.mkdirs()
    }
    return folder
}

fun File.file(name: String): File {
    val file = File(this, name)
    if (!file.exists()) {
        file.createNewFile()
    }
    return file
}

fun retrieveUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, context.packageName + ".provider", file)

fun pubKey(context: Context): ByteArray {
    return context.assets.open("key")
        .use {
            ByteArray(it.available())
                .apply { it.read(this) }
        }
}
