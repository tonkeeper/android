package com.tonapps.signer.core.source

import android.content.Context
import android.util.Log
import com.tonapps.signer.core.entities.KeyEntity
import org.json.JSONArray
import org.json.JSONObject

internal class BackupSource(
    private val context: Context,
) {

    private val backupFile = context.getFileStreamPath("backup.json")

    init {
        if (!backupFile.exists()) {
            backupFile.createNewFile()
        }
    }

    fun add(key: KeyEntity) {
        val data = get() ?: Data(System.currentTimeMillis(), emptyList())
        save(data.copy(keys = data.keys + key))
    }

    fun delete(id: Long) {
        val data = get() ?: return
        val keys = data.keys.filter { it.id != id }
        save(data.copy(keys = keys))
    }

    private fun save(data: Data) {
        if (data.isEmpty) {
            backupFile.delete()
        } else {
            backupFile.writeText(data.string())
        }
    }

    fun get(): Data? {
        if (!backupFile.exists()) {
            return null
        }
        val data = runCatching { Data(backupFile.readText()) }.onFailure {
            backupFile.delete()
        }.getOrNull() ?: return null
        if (data.isEmpty) {
            backupFile.delete()
            return null
        }
        return data
    }

    fun deleteAll() {
        backupFile.delete()
    }

    internal data class Data(
        val timestamp: Long,
        val keys: List<KeyEntity>,
    ) {

        val isEmpty: Boolean
            get() = keys.isEmpty()

        constructor(json: JSONObject) : this(
            timestamp = json.getLong("timestamp"),
            keys = json.getJSONArray("keys").let { keys ->
                List(keys.length()) { index ->
                    KeyEntity(keys.getJSONObject(index))
                }
            }
        )

        constructor(data: String) : this(JSONObject(data))

        private fun toJSON(): JSONObject {
            val array = JSONArray()
            for (key in keys) {
                array.put(key.toJSON())
            }
            return JSONObject().apply {
                put("timestamp", timestamp)
                put("keys", array)
            }
        }

        fun string(): String {
            return toJSON().toString()
        }
    }
}