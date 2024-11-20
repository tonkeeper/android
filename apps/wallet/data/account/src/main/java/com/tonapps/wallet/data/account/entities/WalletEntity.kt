package com.tonapps.wallet.data.account.entities

import android.os.Parcel
import android.os.Parcelable
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.contract.WalletFeature
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.blockchain.ton.extensions.publicKeyFromHex
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.blockchain.ton.extensions.toWalletAddress
import com.tonapps.extensions.readBooleanCompat
import com.tonapps.extensions.readEnum
import com.tonapps.extensions.readParcelableCompat
import com.tonapps.extensions.writeBooleanCompat
import com.tonapps.extensions.writeEnum
import com.tonapps.wallet.data.account.Wallet
import kotlinx.parcelize.Parcelize
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer

data class WalletEntity(
    val id: String,
    val publicKey: PublicKeyEd25519,
    val type: Wallet.Type,
    val version: WalletVersion,
    val label: Wallet.Label,
    val ledger: Ledger? = null,
    val keystone: Keystone? = null,
    val initialized: Boolean,
): Parcelable {

    companion object {

        val EMPTY = WalletEntity(
            id = "",
            publicKey = EmptyPrivateKeyEd25519.publicKey(),
            type = Wallet.Type.Default,
            version = WalletVersion.V5BETA,
            label = Wallet.Label("", "", 0),
            ledger = null,
            keystone = null,
            initialized = false,
        )

        @JvmField
        val CREATOR = object : Parcelable.Creator<WalletEntity> {
            override fun createFromParcel(parcel: Parcel) = WalletEntity(parcel)

            override fun newArray(size: Int): Array<WalletEntity?> = arrayOfNulls(size)
        }

    }

    @Parcelize
    data class Ledger(
        val deviceId: String,
        val accountIndex: Int
    ) : Parcelable

    @Parcelize
    data class Keystone(
        val xfp: String,
        val path: String
    ) : Parcelable

    val contract: BaseWalletContract by lazy {
        val network = if (testnet) TonNetwork.TESTNET.value else TonNetwork.MAINNET.value

        BaseWalletContract.create(publicKey, version.title, network)
    }

    val maxMessages: Int
        get() = if (isLedger) 1 else contract.maxMessages

    val testnet: Boolean
        get() = type == Wallet.Type.Testnet

    val signer: Boolean
        get() = type == Wallet.Type.Signer || type == Wallet.Type.SignerQR

    val hasPrivateKey: Boolean
        get() = type == Wallet.Type.Default || type == Wallet.Type.Testnet || type == Wallet.Type.Lockup

    val accountId: String = contract.address.toAccountId()

    val address: String = contract.address.toWalletAddress(testnet)

    val isWatchOnly: Boolean
        get() = type == Wallet.Type.Watch

    val isLedger: Boolean
        get() = type == Wallet.Type.Ledger

    val isKeystone: Boolean
        get() = type == Wallet.Type.Keystone

    val isW5: Boolean
        get() = version == WalletVersion.V5BETA || version == WalletVersion.V5R1

    val isExternal: Boolean
        get() = signer || isLedger || isKeystone

    val isTonConnectSupported: Boolean
        get() = type != Wallet.Type.Watch // !testnet &&

    constructor(parcel: Parcel) : this(
        id = parcel.readString()!!,
        publicKey = parcel.readString()!!.publicKeyFromHex(),
        type = parcel.readEnum(Wallet.Type::class.java)!!,
        version = parcel.readEnum(WalletVersion::class.java)!!,
        label = parcel.readParcelableCompat()!!,
        ledger = parcel.readParcelableCompat(),
        keystone = parcel.readParcelableCompat(),
        initialized = parcel.readBooleanCompat(),
    )

    fun isMyAddress(address: String): Boolean {
        return address.toRawAddress().equals(accountId, ignoreCase = true)
    }

    fun isSupportedFeature(feature: WalletFeature): Boolean {
        return contract.isSupportedFeature(feature)
    }

    fun createBody(
        seqNo: Int,
        validUntil: Long,
        gifts: List<WalletTransfer>,
        internalMessage: Boolean,
    ): Cell {
        return contract.createTransferUnsignedBody(
            validUntil = validUntil,
            seqNo = seqNo,
            gifts = gifts.toTypedArray(),
            internalMessage = internalMessage,
        )
    }

    fun sign(
        privateKey: PrivateKeyEd25519,
        seqNo: Int,
        body: Cell
    ): Cell {
        return contract.createTransferMessageCell(
            address = contract.address,
            privateKey = privateKey,
            seqNo = seqNo,
            unsignedBody = body,
        )
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(publicKey.hex())
        parcel.writeEnum(type)
        parcel.writeEnum(version)
        parcel.writeParcelable(label, flags)
        parcel.writeParcelable(ledger, flags)
        parcel.writeParcelable(keystone, flags)
        parcel.writeBooleanCompat(initialized)
    }

    override fun describeContents(): Int {
        return 0
    }
}