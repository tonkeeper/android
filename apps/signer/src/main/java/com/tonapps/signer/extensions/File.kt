package com.tonapps.signer.extensions

import android.content.Context
import androidx.core.util.AtomicFile
import androidx.security.crypto.EncryptedFile
import java.io.File

fun File.asAtomicFile(): AtomicFile {
    return AtomicFile(this)
}

fun File.asEncrypted(context: Context, alias: String): EncryptedFile {
    val builder = EncryptedFile.Builder(this, context, alias, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB)
    return builder.build()
}

