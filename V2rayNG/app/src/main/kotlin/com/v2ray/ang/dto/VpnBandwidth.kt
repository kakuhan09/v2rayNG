package com.v2ray.ang.dto

import android.os.Parcel
import android.os.Parcelable
import android.util.Base64


data class VpnBandwidth(val rxByte: Long = 0, val txByte: Long = 0) : Parcelable {
    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<VpnBandwidth> = object : Parcelable.Creator<VpnBandwidth> {
            override fun createFromParcel(source: Parcel): VpnBandwidth = VpnBandwidth(source)
            override fun newArray(size: Int): Array<VpnBandwidth?> = arrayOfNulls(size)
        }
    }

    constructor(source: Parcel) : this(source.readLong(), source.readLong())

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeLong(rxByte)
        dest?.writeLong(txByte)
    }

    operator infix fun minus(other: VpnBandwidth) = VpnBandwidth(
            rxByte - other.rxByte,
            txByte - other.txByte
    )

    operator infix fun plus(other: VpnBandwidth) = VpnBandwidth(
            rxByte + other.rxByte,
            txByte + other.txByte
    )

    fun serializeString(): String {
        val parcel = Parcel.obtain()
        parcel.setDataPosition(0)
        writeToParcel(parcel, 0)
        val ret = Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP)
        parcel.recycle()
        return ret
    }
}