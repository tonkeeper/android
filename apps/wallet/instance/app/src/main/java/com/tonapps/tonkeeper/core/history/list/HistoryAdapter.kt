package com.tonapps.tonkeeper.core.history.list

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.core.comment.CommentEncryption
import com.tonapps.tonkeeper.core.history.list.holder.HistoryActionHolder
import com.tonapps.tonkeeper.core.history.list.holder.HistoryAppHolder
import com.tonapps.tonkeeper.core.history.list.holder.HistoryHeaderHolder
import com.tonapps.tonkeeper.core.history.list.holder.HistoryLoaderHolder
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.ui.screen.dialog.encrypted.EncryptedCommentScreen
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import uikit.navigation.Navigation

open class HistoryAdapter(
    private val disableOpenAction: Boolean = false,
    private val eventsRepository: EventsRepository,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val passcodeManager: PasscodeManager,
) : BaseListAdapter() {

    private val adapterScope = CoroutineScope(Job() + Dispatchers.Main)

    init {
        super.setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        val item = super.getItem(position) as? HistoryItem ?: return RecyclerView.NO_ID
        return item.timestampForSort
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseListHolder<out BaseListItem> {
        adapterScope.launch {
            eventsRepository.decryptedCommentFlow.collect {
                val (txId) = it
                for (i in 0 until itemCount) {
                    val item = getItem(i) as? HistoryItem.Event ?: continue
                    if (item.txId == txId) {
                        notifyItemChanged(i)
                        break
                    }
                }
            }
        }

        return super.onCreateViewHolder(parent, viewType)
    }

    override fun createHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseListHolder<out BaseListItem> {
        return when (viewType) {
            HistoryItem.TYPE_ACTION -> HistoryActionHolder(
                parent,
                disableOpenAction,
                eventsRepository::getDecryptedComment,
                ::decryptComment
            )

            HistoryItem.TYPE_HEADER -> HistoryHeaderHolder(parent)
            HistoryItem.TYPE_LOADER -> HistoryLoaderHolder(parent)
            HistoryItem.TYPE_APP -> HistoryAppHolder(parent)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    private fun doDecryptComment(context: Context, position: Int, txId: String, cipherText: String, senderAddress: String) {
        accountRepository.selectedWalletFlow.combine(
            passcodeManager.confirmationFlow(
                context,
                context.getString(Localization.app_name)
            )
        ) { wallet, _ ->
            val privateKey = accountRepository.getPrivateKey(wallet.id)
            val comment = CommentEncryption.decryptComment(
                wallet.publicKey,
                privateKey,
                cipherText,
                senderAddress
            )
            eventsRepository.setDecryptedComment(txId, comment)
        }.catch {}.take(1).launchIn(adapterScope)
    }

    private fun decryptComment(context: Context, position: Int, txId: String, cipherText: String, senderAddress: String) {
        if (settingsRepository.showEncryptedCommentModal) {
            Navigation.from(context)
                ?.add(EncryptedCommentScreen.newInstance {
                    doDecryptComment(
                        context,
                        position,
                        txId,
                        cipherText,
                        senderAddress
                    )
                })
        } else {
            doDecryptComment(context, position, txId, cipherText, senderAddress)
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        adapterScope.cancel()
    }

}