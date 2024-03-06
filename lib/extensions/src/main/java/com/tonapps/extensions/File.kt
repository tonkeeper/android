package com.tonapps.extensions

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