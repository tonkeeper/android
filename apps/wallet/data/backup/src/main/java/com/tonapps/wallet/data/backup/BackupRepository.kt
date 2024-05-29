package com.tonapps.wallet.data.backup

import android.content.Context
import com.tonapps.wallet.data.backup.entities.BackupEntity
import com.tonapps.wallet.data.backup.source.LocalDataSource
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

class BackupRepository(
    scope: CoroutineScope,
    context: Context
) {

    private val localDataSource = LocalDataSource(context)
    private val _stream = MutableStateFlow<List<BackupEntity>?>(null)
    val stream = _stream.asStateFlow().filterNotNull().shareIn(scope, SharingStarted.Eagerly, 1)

    init {
        scope.launch(Dispatchers.IO) {
            _stream.value = localDataSource.getAllBackups()
        }
    }

    fun updateBackup(id: Long): BackupEntity? {
        val entity = localDataSource.updateBackup(id) ?: return null
        _stream.value = _stream.value?.map { if (it.id == id) entity else it } ?: listOf(entity)
        return entity
    }

    fun addBackup(walletId: Long, source: BackupEntity.Source): BackupEntity {
        val entity = localDataSource.addBackup(walletId, source)
        _stream.value = _stream.value?.plus(entity) ?: listOf(entity)
        return entity
    }
}