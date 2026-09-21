package com.tonapps.wallet.data.multichain.wallet

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tonapps.wallet.data.multichain.account.AccountEntity

@Dao
interface WalletBundleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: CredentialEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallet(wallet: McWalletEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Transaction
    suspend fun insertNewWallet(
        credential: CredentialEntity,
        wallet: McWalletEntity,
        accounts: List<AccountEntity>,
        walletAppCredential: CredentialEntity?,
    ) {
        insertCredential(credential)
        insertWallet(wallet)
        insertAccounts(accounts)
        if (walletAppCredential != null) {
            insertCredential(walletAppCredential)
        }
    }

    @Query("DELETE FROM account WHERE wallet_id = :walletId")
    suspend fun deleteAccounts(walletId: String)

    @Query("DELETE FROM account WHERE wallet_id IN (:walletIds)")
    suspend fun deleteAccounts(walletIds: List<String>)

    @Query("DELETE FROM favorite_asset WHERE wallet_id = :walletId")
    suspend fun deleteFavoriteAssets(walletId: String)

    @Query("DELETE FROM favorite_asset WHERE wallet_id IN (:walletIds)")
    suspend fun deleteFavoriteAssets(walletIds: List<String>)

    @Query("DELETE FROM wallet WHERE id = :walletId")
    suspend fun deleteWalletRow(walletId: String)

    @Query("DELETE FROM wallet WHERE id IN (:walletIds)")
    suspend fun deleteWalletRows(walletIds: List<String>)

    @Query("DELETE FROM credential WHERE id IN (:ids)")
    suspend fun deleteCredentials(ids: List<String>)

    // Mirror of insertNewWallet: everything a wallet owns goes in one transaction, so a process
    // death cannot leave an orphaned credential behind.
    @Transaction
    suspend fun deleteWallet(walletId: String, credentialIds: List<String>) {
        deleteAccounts(walletId)
        deleteFavoriteAssets(walletId)
        deleteWalletRow(walletId)
        deleteCredentials(credentialIds)
    }

    @Transaction
    suspend fun deleteWallets(walletIds: List<String>, credentialIds: List<String>) {
        deleteAccounts(walletIds)
        deleteFavoriteAssets(walletIds)
        deleteWalletRows(walletIds)
        deleteCredentials(credentialIds)
    }
}
