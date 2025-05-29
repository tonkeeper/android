package com.tonapps.tonkeeper.ui.screen.tonconnect

import android.os.Parcelable
import com.tonapps.blockchain.ton.connect.TONProof
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.wallet.data.account.entities.WalletEntity
import kotlinx.parcelize.Parcelize

@Parcelize
data class TonConnectResponse(
    val notifications: Boolean,
    val proof: TONProof.Result?,
    val proofError: BridgeError?,
    val wallet: WalletEntity,
): Parcelable