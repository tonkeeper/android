package com.tonapps.blockchain.ton.connect

import android.os.Parcel
import android.os.Parcelable
import com.tonapps.base64.encodeBase64
import com.tonapps.extensions.readParcelableCompat
import com.tonapps.extensions.toByteArray
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.block.AddrStd
import org.ton.crypto.digest.sha256
import org.ton.crypto.hex
import java.nio.ByteOrder

object TONProof {

    private val prefix = "ton-proof-item-v2/".toByteArray()
    private const val tonConnectPrefix = "ton-connect"
    val prefixMessage = hex("ffff") + tonConnectPrefix.toByteArray()

    fun sign(
        address: AddrStd,
        secretKey: PrivateKeyEd25519,
        payload: String,
        domain: String,
    ): Result {
        val request = Request(
            payload = payload,
            domain = TCDomain(domain),
            address = TCAddress(address)
        )

        val body = sha256(prefixMessage + request.signatureMessage)
        val signature = secretKey.sign(body)

        return Result(
            timestamp = request.timestamp,
            domain = request.domain,
            payload = request.payload,
            signature = signature.encodeBase64()
        )
    }

    data class Request(
        val timestamp: Long = System.currentTimeMillis() / 1000L,
        val payload: String,
        val domain: TCDomain,
        val address: TCAddress
    ) {
        val message = prefix + address.toByteArray(ByteOrder.LITTLE_ENDIAN) + domain.toByteArray(ByteOrder.LITTLE_ENDIAN) + timestamp.toByteArray(ByteOrder.LITTLE_ENDIAN) + payload.toByteArray()


        val signatureMessage by lazy {
            sha256(message)
        }
    }

    data class Result(
        val timestamp: Long,
        val domain: TCDomain,
        val payload: String?,
        val signature: String
    ): Parcelable {

        constructor(parcel: Parcel) : this(
            parcel.readLong(),
            parcel.readParcelableCompat()!!,
            parcel.readString(),
            parcel.readString()!!
        )

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeLong(timestamp)
            parcel.writeParcelable(domain, flags)
            parcel.writeString(payload)
            parcel.writeString(signature)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<Result> {
            override fun createFromParcel(parcel: Parcel): Result {
                return Result(parcel)
            }

            override fun newArray(size: Int): Array<Result?> {
                return arrayOfNulls(size)
            }
        }

    }
}