package com.tonapps.tonkeeper.ui.screen.backup.check

import android.os.Bundle
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowSource
import com.tonapps.extensions.getEnum
import com.tonapps.extensions.putEnum
import uikit.base.BaseArgs

data class BackupCheckArgs(
    val words: Array<String>,
    val backupId: Long,
    val source: WalletFlowSource
): BaseArgs() {

    constructor(bundle: Bundle) : this(
        words = bundle.getStringArray(ARG_WORDS)!!,
        backupId = bundle.getLong(ARG_BACKUP_ID),
        source = bundle.getEnum(ARG_SOURCE, WalletFlowSource.Settings)
    )

    override fun toBundle(): Bundle = Bundle().apply {
        putStringArray(ARG_WORDS, words)
        putLong(ARG_BACKUP_ID, backupId)
        putEnum(ARG_SOURCE, source)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BackupCheckArgs

        if (!words.contentEquals(other.words)) return false
        if (backupId != other.backupId) return false
        if (source != other.source) return false

        return true
    }

    override fun hashCode(): Int {
        var result = words.contentHashCode()
        result = 31 * result + backupId.hashCode()
        result = 31 * result + source.hashCode()
        return result
    }

    private companion object {
        private const val ARG_WORDS = "words"
        private const val ARG_BACKUP_ID = "backup_id"
        private const val ARG_SOURCE = "source"
    }
}
