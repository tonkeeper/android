package com.tonapps.wallet.data.multichain.realtime

internal class RealtimeSeqGuard {

    private val appliedSeq = HashMap<String, Long>()

    fun markApplied(walletId: String, event: String, seq: Long): Boolean {
        val key = "$walletId:$event"
        val previous = appliedSeq[key]
        if (previous != null && seq <= previous) {
            return false
        }
        appliedSeq[key] = seq
        return true
    }
}
