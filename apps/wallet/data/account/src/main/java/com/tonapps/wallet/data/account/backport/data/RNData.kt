package com.tonapps.wallet.data.account.backport.data

import org.json.JSONObject

abstract class RNData {

    abstract fun toJSON(): JSONObject
}