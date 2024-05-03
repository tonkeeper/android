package com.tonapps.extensions

import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.ParcelCompat
import java.io.Serializable

fun <T: Parcelable> Parcel.readParcelableCompat(clazz: Class<T>): T? {
    return ParcelCompat.readParcelable(this, clazz.classLoader, clazz)
}

inline fun <reified T : Parcelable> Parcel.readParcelableCompat(): T? {
    return readParcelableCompat(T::class.java)
}

fun <T : Serializable> Parcel.readSerializableCompat(clazz: Class<T>): T? {
    return ParcelCompat.readSerializable(this, clazz.classLoader, clazz)
}

inline fun <reified T : Serializable> Parcel.readSerializableCompat(): T? {
    return readSerializableCompat(T::class.java)
}

fun Parcel.writeEnum(value: Enum<*>) {
    writeSerializable(value)
}

fun <T: Enum<T>> Parcel.readEnum(clazz: Class<T>): T? {
    return readSerializableCompat(clazz)
}

fun Parcel.readBooleanCompat(): Boolean {
    return ParcelCompat.readBoolean(this)
}

fun Parcel.writeBooleanCompat(value: Boolean) {
    ParcelCompat.writeBoolean(this, value)
}

fun Parcel.writeCharSequenceCompat(value: CharSequence?) {
    writeString(value?.toString())
}

fun Parcel.readCharSequenceCompat(): CharSequence? {
    return readString()
}

fun <T: Parcelable> Parcel.readArrayCompat(clazz: Class<T>): Array<T>? {
    return ParcelCompat.readArray(this, clazz.classLoader, clazz) as Array<T>?
}

fun Parcel.writeArrayCompat(list: Array<Parcelable>) {
    writeArray(list)
}