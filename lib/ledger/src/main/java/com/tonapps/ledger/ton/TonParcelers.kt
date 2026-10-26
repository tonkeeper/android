package com.tonapps.ledger.ton

import android.os.Parcel
import com.tonapps.blockchain.ton.extensions.toBoc
import kotlinx.io.bytestring.ByteString
import kotlinx.parcelize.Parceler
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.MsgAddress
import org.ton.block.StateInit
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.kotlin.crypto.PublicKeyEd25519
import org.ton.tlb.TlbCodec

private fun Parcel.writeCell(cell: Cell?) {
    writeByteArray(cell?.toBoc())
}

private fun Parcel.readCell(): Cell? = createByteArray()?.let { BagOfCells(it).first() }

internal object CellParceler : Parceler<Cell> {

    override fun create(parcel: Parcel): Cell = requireNotNull(parcel.readCell())

    override fun Cell.write(parcel: Parcel, flags: Int) {
        parcel.writeCell(this)
    }
}

internal object NullableCellParceler : Parceler<Cell?> {

    override fun create(parcel: Parcel): Cell? = parcel.readCell()

    override fun Cell?.write(parcel: Parcel, flags: Int) {
        parcel.writeCell(this)
    }
}

internal abstract class TlbParceler<T : Any>(private val codec: TlbCodec<T>) : Parceler<T> {

    override fun create(parcel: Parcel): T = codec.loadTlb(requireNotNull(parcel.readCell()))

    override fun T.write(parcel: Parcel, flags: Int) {
        parcel.writeCell(codec.createCell(this))
    }
}

internal abstract class NullableTlbParceler<T : Any>(private val codec: TlbCodec<T>) : Parceler<T?> {

    override fun create(parcel: Parcel): T? = parcel.readCell()?.let(codec::loadTlb)

    override fun T?.write(parcel: Parcel, flags: Int) {
        parcel.writeCell(this?.let(codec::createCell))
    }
}

internal object AddrStdParceler : TlbParceler<AddrStd>(AddrStd)

internal object MsgAddressParceler : TlbParceler<MsgAddress>(MsgAddress)

internal object CoinsParceler : TlbParceler<Coins>(Coins)

internal object NullableStateInitParceler : NullableTlbParceler<StateInit>(StateInit)

internal object PublicKeyEd25519Parceler : Parceler<PublicKeyEd25519> {

    override fun create(parcel: Parcel) = PublicKeyEd25519(ByteString(requireNotNull(parcel.createByteArray())))

    override fun PublicKeyEd25519.write(parcel: Parcel, flags: Int) {
        parcel.writeByteArray(key.toByteArray())
    }
}
