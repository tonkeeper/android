package com.tonapps.tonkeeper.ui.screen.backup.check

import android.os.Bundle
import uikit.base.BaseArgs

data class BackupCheckArgs(
    val words: Array<String>,
    val backupId: Long
): BaseArgs() {

    private companion object {
        private const val ARG_WORDS = "words"
        private const val ARG_BACKUP_ID = "backup_id"
    }

    constructor(bundle: Bundle) : this(
        words = bundle.getStringArray(ARG_WORDS)!!,
        backupId = bundle.getLong(ARG_BACKUP_ID)
    )

    override fun toBundle(): Bundle = Bundle().apply {
        putStringArray(ARG_WORDS, words)
        putLong(ARG_BACKUP_ID, backupId)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BackupCheckArgs

        if (!words.contentEquals(other.words)) return false
        if (backupId != other.backupId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = words.contentHashCode()
        result = 31 * result + backupId.hashCode()
        return result
    }
}