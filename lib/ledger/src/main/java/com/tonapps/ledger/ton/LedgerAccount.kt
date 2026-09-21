package com.tonapps.ledger.ton

import android.os.Parcelable
import org.ton.kotlin.crypto.PublicKeyEd25519
import org.ton.block.AddrStd
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class LedgerAccount(
    val address: @RawValue AddrStd,
    val publicKey: @RawValue PublicKeyEd25519,
    val path: AccountPath
): Parcelable
