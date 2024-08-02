package com.tonapps.wallet.data.backup

import android.content.Context
import android.util.Log
import com.tonapps.extensions.isMainVersion
import com.tonapps.wallet.data.backup.entities.BackupEntity
import com.tonapps.wallet.data.backup.source.LocalDataSource
import com.tonapps.wallet.data.rn.RNLegacy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.json.JSONObject

class BackupRepository(
    private val scope: CoroutineScope,
    context: Context,
    private val rnLegacy: RNLegacy,
) {

    private val localDataSource = LocalDataSource(context)
    private val _stream = MutableStateFlow<List<BackupEntity>?>(null)
    val stream = _stream.asStateFlow().filterNotNull().shareIn(scope, SharingStarted.Eagerly, 1)

    init {
        scope.launch(Dispatchers.IO) {
            if (rnLegacy.isRequestMigration()) {
                localDataSource.clear()
                migrationFromRN()
            }
            _stream.value = localDataSource.getAllBackups()
        }
    }

    private suspend fun migrationFromRN() {
        val backups = mutableListOf<BackupEntity>()
        val wallets = rnLegacy.getWallets().wallets
        for (wallet in wallets) {
            val key = "${wallet.identifier}/setup"
            val value = rnLegacy.getJSONValue(key) ?: continue
            val lastBackupAt = value.optLong("lastBackupAt", -1)
            if (lastBackupAt >= 0) {
                backups.add(addBackup(wallet.identifier, BackupEntity.Source.LOCAL, lastBackupAt))
            }
        }
        _stream.value = backups
    }

    private fun backportToRN(entities: List<BackupEntity>?) {
        scope.launch(Dispatchers.IO) {
            val wallets = rnLegacy.getWallets().wallets
            for (wallet in wallets) {
                val entity = entities?.find { it.walletId == wallet.identifier } ?: continue
                val lastBackupAt = entity.date
                val key = "${wallet.identifier}/setup"
                val json = JSONObject()
                json.put("lastBackupAt", lastBackupAt)
                rnLegacy.setJSONValue(key, json)
            }
        }
    }

    fun updateBackup(id: Long): BackupEntity? {
        val entity = localDataSource.updateBackup(id) ?: return null
        _stream.value = _stream.value?.map { if (it.id == id) entity else it } ?: listOf(entity)
        backportToRN(_stream.value)
        return entity
    }

    fun addBackup(
        walletId: String,
        source: BackupEntity.Source,
        date: Long = System.currentTimeMillis()
    ): BackupEntity {
        val entity = localDataSource.addBackup(walletId, source, date)
        _stream.value = _stream.value?.plus(entity) ?: listOf(entity)
        backportToRN(_stream.value)
        return entity
    }
}