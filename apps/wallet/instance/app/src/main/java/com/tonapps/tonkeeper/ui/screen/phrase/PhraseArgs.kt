package com.tonapps.tonkeeper.ui.screen.phrase

import android.os.Bundle
import uikit.base.BaseArgs

data class PhraseArgs(
    val words: Array<String>,
    val backup: Boolean,
    val backupId: Long,
    val isTron: Boolean
): BaseArgs() {

    private companion object {
        private const val ARG_WORDS = "words"
        private const val ARG_BACKUP = "backup"
        private const val ARG_BACKUP_ID = "backup_id"
        private const val ARG_IS_TRON = "is_tron"
    }

    constructor(bundle: Bundle) : this(
        words = bundle.getStringArray(ARG_WORDS)!!,
        backup = bundle.getBoolean(ARG_BACKUP),
        backupId = bundle.getLong(ARG_BACKUP_ID),
        isTron = bundle.getBoolean(ARG_IS_TRON)
    )

    override fun toBundle(): Bundle = Bundle().apply {
        putStringArray(ARG_WORDS, words)
        putBoolean(ARG_BACKUP, backup)
        putLong(ARG_BACKUP_ID, backupId)
        putBoolean(ARG_IS_TRON, isTron)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PhraseArgs

        if (!words.contentEquals(other.words)) return false
        if (backup != other.backup) return false
        if (backupId != other.backupId) return false
        if (isTron != other.isTron) return false

        return true
    }

    override fun hashCode(): Int {
        var result = words.contentHashCode()
        result = 31 * result + backup.hashCode()
        result = 31 * result + backupId.hashCode()
        return result
    }
}