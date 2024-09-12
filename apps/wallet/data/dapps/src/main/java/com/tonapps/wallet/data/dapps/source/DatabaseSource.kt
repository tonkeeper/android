package com.tonapps.wallet.data.dapps.source

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.tonapps.sqlite.SQLiteHelper

internal class DatabaseSource(context: Context): SQLiteHelper(context, "dapps", 1) {

    private companion object {

    }

    override fun create(db: SQLiteDatabase) {

    }


    fun getApps(accountId: String) {

    }

}