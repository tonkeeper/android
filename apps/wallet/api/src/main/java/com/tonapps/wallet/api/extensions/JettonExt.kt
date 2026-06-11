package com.tonapps.wallet.api.extensions

import androidx.core.net.toUri
import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.TokenEntity.Extension
import com.tonapps.blockchain.model.legacy.TokenEntity.Lock
import com.tonapps.blockchain.model.legacy.TokenEntity.Verification
import com.tonapps.blockchain.ton.extensions.toRawAddress
import io.tonapi.models.JettonBalanceLock
import io.tonapi.models.JettonInfo
import io.tonapi.models.JettonPreview
import io.tonapi.models.JettonVerificationType

// TODO move
fun JettonPreview.toTokenEntity(
    extensions: List<String>? = null,
    lock: JettonBalanceLock? = null
): TokenEntity {
    val jetton = this
    return TokenEntity(
        blockchain = Blockchain.TON,
        address = jetton.address.toRawAddress(),
        name = jetton.name,
        symbol = jetton.symbol,
        imageUri = jetton.image.toUri(),
        decimals = jetton.decimals,
        verification = jetton.verification.convertVerification(),
        isRequestMinting = extensions?.contains(Extension.CustomPayload.value) == true,
        isTransferable = extensions?.contains(Extension.NonTransferable.value) != true,
        lock = lock?.let { Lock(it.amount, it.till) },
        customPayloadApiUri = jetton.customPayloadApiUri,
        numerator = jetton.scaledUi?.numerator?.toBigDecimal(),
        denominator = jetton.scaledUi?.denominator?.toBigDecimal()
    )
}

// TODO move
fun JettonInfo.toTokenEntity(
    extensions: List<String>? = null,
    lock: JettonBalanceLock? = null,
): TokenEntity {
    val jetton = this
    return TokenEntity(
        blockchain = Blockchain.TON,
        address = jetton.metadata.address.toRawAddress(),
        name = jetton.metadata.name,
        symbol = jetton.metadata.symbol,
        imageUri = jetton.preview.toUri(),
        decimals = jetton.metadata.decimals.toInt(),
        verification = jetton.verification.convertVerification(),
        isRequestMinting = extensions?.contains(Extension.CustomPayload.value) == true,
        isTransferable = extensions?.contains(Extension.NonTransferable.value) != true,
        lock = lock?.let { Lock(it.amount, it.till) },
        customPayloadApiUri = jetton.metadata.customPayloadApiUri
    )
}


private fun JettonVerificationType.convertVerification(): Verification {
    return when (this) {
        JettonVerificationType.whitelist -> Verification.whitelist
        JettonVerificationType.blacklist -> Verification.blacklist
        else -> Verification.none
    }
}
