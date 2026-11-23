package com.tonapps.deposit.multicoin.domain

import com.tonapps.wallet.data.multichain.account.McAccountRepository

class RampWalletAddress(
    private val mcAccountRepository: McAccountRepository,
) {

    suspend fun of(walletId: String, assetId: String): String {
        val address = mcAccountRepository.findAccount(walletId, assetId)?.data?.displayAddress
        return requireNotNull(address?.takeIf { it.isNotBlank() }) { "No account for $assetId" }
    }
}
