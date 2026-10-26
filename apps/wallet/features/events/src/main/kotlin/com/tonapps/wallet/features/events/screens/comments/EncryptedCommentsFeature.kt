package com.tonapps.wallet.features.events.screens.comments

import android.content.Context
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.features.events.data.EncryptedCommentDecryptor
import com.tonapps.wallet.features.events.data.EncryptedCommentEntity
import kotlinx.coroutines.flow.StateFlow

interface EncryptedCommentsState : MviViewState {
    val decrypted: StateFlow<Map<String, String>>
}

sealed interface EncryptedCommentsAction : MviAction {
    class Decrypt(
        val context: Context,
        val walletId: String,
        val comment: EncryptedCommentEntity,
    ) : EncryptedCommentsAction
}

class EncryptedCommentsFeature(
    private val decryptor: EncryptedCommentDecryptor,
    eventsRepository: EventsRepository,
) : GraphViewModel<EncryptedCommentsState, EncryptedCommentsAction>(), EncryptedCommentsState {

    override val decrypted = eventsRepository.decryptedCommentFlow
        .cacheState(initialValue = emptyMap())

    private val decryptState = actions
        .on<EncryptedCommentsAction.Decrypt>()
        .transformStateFirst<EncryptedCommentsAction.Decrypt, Boolean?> {
            decryptor.decrypt(it.context, it.walletId, it.comment)
        }
        .cacheStateWithEmpty()
}

/*
 *   EventsRepository.decryptedCommentFlow ──► decrypted
 *
 *   Decrypt ──► [first] decryptState?
 */
