package com.tonapps.wallet.data.events.source

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.sqlite.SQLiteHelper
import com.tonapps.sqlite.withTransaction
import com.tonapps.wallet.api.fromJSON
import com.tonapps.wallet.api.toJSON
import io.tonapi.models.AccountEvent
import kotlinx.coroutines.CoroutineScope

internal class DatabaseSource(
    scope: CoroutineScope,
    context: Context
): SQLiteHelper(context, DATABASE_NAME, DATABASE_VERSION) {

    private companion object {
        private const val DATABASE_NAME = "events.db"
        private const val DATABASE_VERSION = 1

        private const val SPAM_TABLE_NAME = "spam"
        private const val SPAM_TABLE_EVENT_ID_COLUMN = "event_id"
        private const val SPAM_TABLE_ACCOUNT_ID_COLUMN = "account_id"
        private const val SPAM_TABLE_TESTNET_COLUMN = "testnet"
        private const val SPAM_TABLE_BODY_COLUMN = "body"
        private const val SPAM_TABLE_DATE_COLUMN = "date"

        private val spamFields = arrayOf(
            SPAM_TABLE_BODY_COLUMN
        ).joinToString(",")

        private fun AccountEvent.toValues(accountId: String, testnet: Boolean): ContentValues {
            val values = ContentValues()
            values.put(SPAM_TABLE_EVENT_ID_COLUMN, eventId)
            values.put(SPAM_TABLE_ACCOUNT_ID_COLUMN, accountId.toRawAddress())
            values.put(SPAM_TABLE_TESTNET_COLUMN, if (testnet) 1 else 0)
            values.put(SPAM_TABLE_BODY_COLUMN, toJSON(this))
            values.put(SPAM_TABLE_DATE_COLUMN, timestamp)
            return values
        }
    }

    override fun create(db: SQLiteDatabase) {
        createSpamTable(db)
    }

    private fun createSpamTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $SPAM_TABLE_NAME (" +
                "$SPAM_TABLE_EVENT_ID_COLUMN TEXT NOT NULL UNIQUE, " +
                "$SPAM_TABLE_ACCOUNT_ID_COLUMN TEXT NOT NULL, " +
                "$SPAM_TABLE_TESTNET_COLUMN INTEGER NOT NULL, " +
                "$SPAM_TABLE_BODY_COLUMN BLOB," +
                "$SPAM_TABLE_DATE_COLUMN INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))" +
                ")")

        val spamIndexPrefix = "idx_${SPAM_TABLE_NAME}"
        db.execSQL("CREATE INDEX ${spamIndexPrefix}_account_id_testnet ON $SPAM_TABLE_NAME ($SPAM_TABLE_ACCOUNT_ID_COLUMN, $SPAM_TABLE_TESTNET_COLUMN)")
    }

    fun addSpam(accountId: String, testnet: Boolean, events: List<AccountEvent>) {
        writableDatabase.withTransaction {
            for (event in events) {
                writableDatabase.insert(SPAM_TABLE_NAME, null, event.toValues(accountId, testnet))
            }
        }
    }

    fun getSpam(accountId: String, testnet: Boolean): List<AccountEvent> {
        val query = "SELECT $spamFields FROM $SPAM_TABLE_NAME WHERE $SPAM_TABLE_ACCOUNT_ID_COLUMN = ? AND $SPAM_TABLE_TESTNET_COLUMN = ? ORDER BY $SPAM_TABLE_DATE_COLUMN DESC LIMIT 25"
        val cursor = readableDatabase.rawQuery(query, arrayOf(accountId.toRawAddress(), if (testnet) "1" else "0"))
        val bodyIndex = cursor.getColumnIndex(SPAM_TABLE_BODY_COLUMN)
        val events = mutableListOf<AccountEvent>()
        while (cursor.moveToNext()) {
            val body = cursor.getString(bodyIndex)
            events.add(fromJSON(body))
        }
        cursor.close()
        return events.toList()
    }
}