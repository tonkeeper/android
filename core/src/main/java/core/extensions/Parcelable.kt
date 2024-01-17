package core.extensions

import android.os.Parcel
import android.os.Parcelable

fun Parcelable.toByteArray(): ByteArray {
    val parcel = Parcel.obtain()
    writeToParcel(parcel, 0)
    val bytes = parcel.marshall()
    parcel.recycle()
    return bytes
}

fun ByteArray.toParcel(): Parcel {
    val parcel = Parcel.obtain()
    parcel.unmarshall(this, 0, size)
    parcel.setDataPosition(0)
    return parcel
}

fun <T: Parcelable> ByteArray.toParcel(creator: Parcelable.Creator<T>): T {
    val parcel = toParcel()
    return creator.createFromParcel(parcel)
}
