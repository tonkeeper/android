package com.tonapps.tonkeeper.ui.screen.backup.main.list

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.backup.main.list.holder.BackupHolder
import com.tonapps.tonkeeper.ui.screen.backup.main.list.holder.HeaderHolder
import com.tonapps.tonkeeper.ui.screen.backup.main.list.holder.ManualHolder
import com.tonapps.tonkeeper.ui.screen.backup.main.list.holder.RecoveryPhraseHolder
import com.tonapps.tonkeeper.ui.screen.backup.main.list.holder.SpaceHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class Adapter(
    private val onClick: (Item) -> Unit
): BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when(viewType) {
            Item.TYPE_HEADER -> HeaderHolder(parent)
            Item.TYPE_BACKUP -> BackupHolder(parent, onClick)
            Item.TYPE_RECOVERY_PHRASE -> RecoveryPhraseHolder(parent, onClick)
            Item.TYPE_SPACE -> SpaceHolder(parent)
            Item.TYPE_MANUAL_BACKUP -> ManualHolder(parent, onClick)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

}