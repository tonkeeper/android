package com.tonapps.wallet.data.multichain.account

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tonapps.chainkit.core.chain.model.account.Chain

@Dao
interface AccountDao {
    @Query("SELECT * FROM account WHERE wallet_id = :walletId AND network = :network AND mode = :mode")
    suspend fun getAccount(walletId: String, network: String, mode: String): AccountEntity?

    suspend fun getAccount(walletId: String, chain: Chain): AccountEntity? {
        return getAccount(walletId, chain.network.type.id, chain.network.mode.id)
    }

    @Query("SELECT * FROM account WHERE wallet_id = :walletId")
    suspend fun getAccountsByWalletId(walletId: String): List<AccountEntity>

    @Query("SELECT * FROM account WHERE network = :network AND mode = :mode")
    suspend fun getAccounts(network: String, mode: String): List<AccountEntity>

    suspend fun getAccounts(chain: Chain): List<AccountEntity> {
        return getAccounts(chain.network.type.id, chain.network.mode.id)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Query("DELETE FROM account WHERE wallet_id = :walletId")
    suspend fun deleteByWalletId(walletId: String)
}
