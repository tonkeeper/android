package com.tonapps.tonkeeper.helper

import android.content.Context
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import java.nio.charset.Charset

class NFCHelper(context: Context) {

    fun addWallet(address: String) {
        val deepLink = "ton://transfer/$address"

        NdefRecord(
            NdefRecord.TNF_ABSOLUTE_URI,
            deepLink.toByteArray(Charset.forName("US-ASCII")),
            byteArrayOf(),
            byteArrayOf()
        )


        NfcAdapter.ACTION_TAG_DISCOVERED
        val ndefRecord = NdefRecord.createUri(deepLink)
    }
}