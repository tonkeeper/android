package com.tonapps.wallet.features.events.data

import android.content.Context

interface EncryptedCommentDecryptor {
    suspend fun decrypt(context: Context, walletId: String, comment: EncryptedCommentEntity): Boolean
}
