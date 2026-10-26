package com.tonapps.ledger.ton

import android.os.Parcelable
import org.ton.kotlin.crypto.PublicKeyEd25519
import org.ton.block.AddrStd
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

@Parcelize
@TypeParceler<AddrStd, AddrStdParceler>
@TypeParceler<PublicKeyEd25519, PublicKeyEd25519Parceler>
data class LedgerAccount(
    val address: AddrStd,
    val publicKey: PublicKeyEd25519,
    val path: AccountPath
): Parcelable
