package com.tonapps.wallet.data.events.source

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.extensions.toByteArray
import com.tonapps.sqlite.SQLiteHelper
import com.tonapps.sqlite.withTransaction
import com.tonapps.wallet.api.entity.value.BlockchainAddress
import com.tonapps.wallet.data.events.tx.model.TxEvent
import io.Serializer
import io.tonapi.models.AccountEvent
import kotlinx.coroutines.CoroutineScope

internal class DatabaseSource(
    scope: CoroutineScope,
    context: Context
): SQLiteHelper(context, DATABASE_NAME, DATABASE_VERSION) {

    private companion object {
        private const val DATABASE_NAME = "events.db"
        private const val DATABASE_VERSION = 2

        private const val SPAM_TABLE_NAME = "spam"
        private const val SPAM_TABLE_EVENT_ID_COLUMN = "event_id"
        private const val SPAM_TABLE_ACCOUNT_ID_COLUMN = "account_id"
        private const val SPAM_TABLE_TESTNET_COLUMN = "testnet"
        private const val SPAM_TABLE_BODY_COLUMN = "body"
        private const val SPAM_TABLE_DATE_COLUMN = "date"

        private const val EVENTS_TABLE_NAME = "events"
        private const val EVENTS_TABLE_EVENT_ID_COLUMN = "event_id"
        private const val EVENTS_TABLE_ACCOUNT_KEY_COLUMN = "account_key"
        private const val EVENTS_TABLE_BODY_COLUMN = "body"
        private const val EVENTS_TABLE_DATE_COLUMN = "date"

        private val spamFields = arrayOf(
            SPAM_TABLE_BODY_COLUMN
        ).joinToString(",")

        private val eventsFields = arrayOf(
            EVENTS_TABLE_BODY_COLUMN
        ).joinToString(",")

        private fun AccountEvent.toValues(accountId: String, testnet: Boolean): ContentValues {
            val values = ContentValues()
            values.put(SPAM_TABLE_EVENT_ID_COLUMN, eventId)
            values.put(SPAM_TABLE_ACCOUNT_ID_COLUMN, accountId.toRawAddress())
            values.put(SPAM_TABLE_TESTNET_COLUMN, if (testnet) 1 else 0)
            values.put(SPAM_TABLE_BODY_COLUMN, Serializer.toJSON(this))
            values.put(SPAM_TABLE_DATE_COLUMN, timestamp)
            return values
        }

        private fun TxEvent.toValues(address: BlockchainAddress): ContentValues {
            val values = ContentValues()
            values.put(EVENTS_TABLE_EVENT_ID_COLUMN, id)
            values.put(EVENTS_TABLE_ACCOUNT_KEY_COLUMN, address.key)
            values.put(EVENTS_TABLE_BODY_COLUMN, toByteArray())
            values.put(EVENTS_TABLE_DATE_COLUMN, timestamp.toLong())
            return values
        }
    }

    override fun create(db: SQLiteDatabase) {
        createSpamTable(db)
        createEventsTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        super.onUpgrade(db, oldVersion, newVersion)
        if (oldVersion < 2) {
            createEventsTable(db)
        }
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

    private fun createEventsTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $EVENTS_TABLE_NAME (" +
                "$EVENTS_TABLE_EVENT_ID_COLUMN TEXT NOT NULL UNIQUE, " +
                "$EVENTS_TABLE_ACCOUNT_KEY_COLUMN TEXT NOT NULL, " +
                "$EVENTS_TABLE_BODY_COLUMN BLOB," +
                "$EVENTS_TABLE_DATE_COLUMN INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))" +
                ")")

        val eventsIndexPrefix = "idx_${EVENTS_TABLE_NAME}"
        db.execSQL("CREATE INDEX ${eventsIndexPrefix}_account ON $EVENTS_TABLE_NAME($EVENTS_TABLE_ACCOUNT_KEY_COLUMN);")
        db.execSQL("CREATE INDEX ${eventsIndexPrefix}_account_date ON $EVENTS_TABLE_NAME($EVENTS_TABLE_ACCOUNT_KEY_COLUMN, $EVENTS_TABLE_DATE_COLUMN DESC);")
    }

    fun insertEvent(address: BlockchainAddress, events: List<TxEvent>) {
        writableDatabase.withTransaction {
            for (event in events) {
                writableDatabase.insertWithOnConflict(EVENTS_TABLE_NAME, null, event.toValues(address), SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    fun addSpam(accountId: String, testnet: Boolean, events: List<AccountEvent>) {
        writableDatabase.withTransaction {
            for (event in events) {
                writableDatabase.insertWithOnConflict(SPAM_TABLE_NAME, null, event.toValues(accountId, testnet), SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    fun removeSpam(accountId: String, testnet: Boolean, eventId: String) {
        writableDatabase.withTransaction {
            writableDatabase.delete(SPAM_TABLE_NAME, "$SPAM_TABLE_ACCOUNT_ID_COLUMN = ? AND $SPAM_TABLE_TESTNET_COLUMN = ? AND $SPAM_TABLE_EVENT_ID_COLUMN = ?", arrayOf(accountId.toRawAddress(), if (testnet) "1" else "0", eventId))
        }
    }

    fun getSpam(accountId: String, testnet: Boolean): List<AccountEvent> {
        val query = "SELECT $spamFields FROM $SPAM_TABLE_NAME WHERE $SPAM_TABLE_ACCOUNT_ID_COLUMN = ? AND $SPAM_TABLE_TESTNET_COLUMN = ? ORDER BY $SPAM_TABLE_DATE_COLUMN DESC LIMIT 25"
        val cursor = readableDatabase.rawQuery(query, arrayOf(accountId.toRawAddress(), if (testnet) "1" else "0"))
        val bodyIndex = cursor.getColumnIndex(SPAM_TABLE_BODY_COLUMN)
        val events = mutableListOf<AccountEvent>()
        while (cursor.moveToNext()) {
            try {
                val body = cursor.getString(bodyIndex)
                events.add(Serializer.fromJSON(body))
            } catch (ignored: Throwable) { }
        }
        cursor.close()
        return events.toList()
    }
}