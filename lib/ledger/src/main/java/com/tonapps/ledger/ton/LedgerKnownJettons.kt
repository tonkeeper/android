package com.tonapps.ledger.ton

import org.ton.block.AddrStd

object LedgerKnownJettons {

    private const val BASE_APP_VERSION = "2.3.0"
    private const val EXTENDED_APP_VERSION = "2.6.1"

    private class Entry(master: String, val minAppVersion: String) {
        val master: AddrStd = AddrStd.parse(master)
    }

    private val entries: List<Entry> = listOf(
        Entry("EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs", BASE_APP_VERSION), // USDT, Tether USD
        Entry("EQAvlWFDxGF2lXm67y4yzC17wYKD9A0guwPkMs1gOsM__NOT", BASE_APP_VERSION), // NOT, Notcoin
        Entry("EQC98_qAmNEptUtPc7W6xdHh_ZHrBUFpw5Ft_IzNU20QAJav", BASE_APP_VERSION), // tsTON, Tonstakers TON
        Entry("EQB0SoxuGDx5qjVt0P_bPICFeWdFLBmVopHhjgfs0q-wsTON", BASE_APP_VERSION), // wsTON, Ton Whales liquid staking token
        Entry("EQDPdq8xjAhytYqfGSX8KcFWIReCufsB9Wdg0pLlYSO_h76w", BASE_APP_VERSION), // hTON, Hipo Staked TON
        Entry("EQDNhy-nxYFgUqzfUzImBEP67JqsyMIcyk2S5_RwNNEYku0k", BASE_APP_VERSION), // stTON, bemo Staked TON
        Entry("EQCqC6EhRJ_tpWngKxL6dV0k6DSnRUrs9GSVkLbfdCqsj6TE", BASE_APP_VERSION), // STAKED, Staked TON
        Entry("EQD-cvR0Nz6XAyRBvbhz-abTrRC6sI5tvHvvpeQraV9UAAD7", EXTENDED_APP_VERSION), // CATI, Catizen
        Entry("EQCvxJy4eG8hyHBFsZ7eePxrRsUQSFE_jpptRAYBmcG_DOGS", EXTENDED_APP_VERSION), // DOGS, Dogs
        Entry("EQB4zZusHsbU2vVTPqjhlokIOoiZhEdCMT703CWEzhTOo__X", EXTENDED_APP_VERSION), // X, X Empire
    )

    fun jettonId(master: AddrStd): Int? = entries.indexOfFirst { it.master == master }.takeIf { it >= 0 }

    fun minAppVersion(jettonId: Int): String = entries[jettonId].minAppVersion
}
