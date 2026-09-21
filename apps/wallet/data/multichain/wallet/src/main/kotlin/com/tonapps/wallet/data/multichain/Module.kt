package com.tonapps.wallet.data.multichain

import com.tonapps.security.multichain.McPasscodeStore
import com.tonapps.security.multichain.SessionKeyCipher
import com.tonapps.security.multichain.SessionKeyStore
import com.tonapps.security.multichain.VaultStorage
import com.tonapps.wallet.api.API
import com.tonapps.chainkit.core.net.module.SessionTokenProvider
import com.tonapps.wallet.PendingTransactionReporter
import com.tonapps.wallet.api.AuthorizationProvider
import com.tonapps.wallet.data.multichain.account.AccountRepositoryImpl
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepositoryImpl
import com.tonapps.wallet.data.multichain.db.AppDatabase
import com.tonapps.wallet.data.multichain.device.DeviceSessionTokenProvider
import com.tonapps.wallet.data.multichain.device.SecureDeviceRepository
import com.tonapps.wallet.data.multichain.realtime.McWalletRealtimeProvider
import com.tonapps.wallet.data.multichain.tx.PostTransactionRefreshSchedule
import com.tonapps.wallet.data.multichain.tx.RemotePendingTransactionReporter
import com.tonapps.wallet.data.multichain.vault.VaultDatabase
import com.tonapps.wallet.data.multichain.vault.WalletKeyRepository
import com.tonapps.wallet.data.multichain.vault.VaultRepository
import org.koin.dsl.bind
import org.koin.dsl.module

val mcWalletModule = module {
    single { AppDatabase.instance(get()) }
    single { get<AppDatabase>().accountDao() }
    single { get<AppDatabase>().walletDao() }
    single { get<AppDatabase>().credentialDao() }
    single { get<AppDatabase>().walletBundleDao() }
    single { get<AppDatabase>().favoriteAssetDao() }
    single { VaultDatabase.instance(get()) }
    single { SessionKeyStore() }
    single { VaultRepository(get<AppDatabase>().vaultMetadataDao(), get()) }
    single<VaultStorage> { get<VaultRepository>() }
    single { SessionKeyCipher(get()) }
    single { McPasscodeStore(get(), get(), get()) }
    single { WalletKeyRepository(get(), get()) }
    single(createdAtStart = true) {
        SecureDeviceRepository(get(), get(), get(), get()).also { get<API>().setDeviceAuthProvider(it) }
    }
    single<SessionTokenProvider> { DeviceSessionTokenProvider(get()) }
    single<McAccountRepository> { AccountRepositoryImpl(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single<UnifiedAccountRepository> { UnifiedAccountRepositoryImpl(get(), get(), get(), get()) }
    single<AuthorizationProvider> { get<UnifiedAccountRepository>() }
    single { RemotePendingTransactionReporter(get()) } bind PendingTransactionReporter::class
    single(createdAtStart = true) { McWalletRealtimeProvider(get(), get(), get(), get()).also { it.start() } }
    single { PostTransactionRefreshSchedule(get(), get(), get()) }
}
