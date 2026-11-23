package com.tonapps.wallet.data.loyalty

import android.content.Context
import androidx.core.content.edit
import com.tonapps.core.flags.WalletFeature
import com.tonapps.log.L
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.LoyaltyProfileEntity
import com.tonapps.wallet.data.core.BlobDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

class LoyaltyRepository(
    private val context: Context,
    private val api: API,
) {

    private val profileCache = BlobDataSource.simpleJSON<LoyaltyProfileEntity>(context, PROFILE_CACHE_PATH)

    private val profiles = ConcurrentHashMap<String, LoyaltyProfileEntity>()

    private val preferences by lazy {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    private val bannerHiddenWallets by lazy {
        MutableStateFlow(preferences.getStringSet(KEY_BANNER_HIDDEN_WALLETS, null).orEmpty())
    }

    private val referralCodeLock = Any()

    fun entryFlow(walletId: String): Flow<LoyaltyEntry> = flow {
        if (!WalletFeature.Loyalty.isEnabled) {
            emit(LoyaltyEntry.Hidden)
            return@flow
        }
        val entries = combine(profileFlow(walletId), bannerHiddenWallets) { profile, hiddenWallets ->
            LoyaltyEntry.of(profile, isBannerHidden = walletId in hiddenWallets)
        }
        emitAll(entries)
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    fun hideBanner(walletId: String) {
        synchronized(bannerHiddenWallets) {
            val hiddenWallets = bannerHiddenWallets.updateAndGet { it + walletId }
            preferences.edit { putStringSet(KEY_BANNER_HIDDEN_WALLETS, hiddenWallets) }
        }
    }

    suspend fun showsSection(walletId: String): Boolean = withContext(Dispatchers.IO) {
        if (!WalletFeature.Loyalty.isEnabled) {
            return@withContext false
        }
        val profile = cachedProfile(walletId) ?: loadProfile(walletId)
        LoyaltyEntry.of(profile, isBannerHidden = false).showsSection
    }

    suspend fun saveReferralCode(code: String) = withContext(Dispatchers.IO) {
        synchronized(referralCodeLock) {
            if (preferences.getString(KEY_REFERRAL_CODE, null) == null) {
                preferences.edit { putString(KEY_REFERRAL_CODE, code) }
            }
        }
    }

    suspend fun takeReferralCode(): String? = withContext(Dispatchers.IO) {
        synchronized(referralCodeLock) {
            preferences.getString(KEY_REFERRAL_CODE, null)?.also {
                preferences.edit { remove(KEY_REFERRAL_CODE) }
            }
        }
    }

    suspend fun perpsReward(walletId: String, volumeUsd: BigDecimal): LoyaltyReward = withContext(Dispatchers.IO) {
        if (!showsSection(walletId)) {
            return@withContext LoyaltyReward.Hidden
        }
        try {
            val ohms = api.loyalty.getRewardEstimate(walletId, PERPS_REWARD_KIND, volumeUsd.toPlainString())
                .total
                .toPositiveOhmsOrNull()
            if (ohms != null) {
                LoyaltyReward.Ohms(ohms)
            } else {
                LoyaltyReward.Hidden
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.w(e, "Loyalty reward estimate is unavailable")
            LoyaltyReward.Unavailable
        }
    }

    private fun profileFlow(walletId: String): Flow<LoyaltyProfileEntity?> = flow {
        emit(cachedProfile(walletId))
        loadProfile(walletId)?.let { emit(it) }
    }

    private fun cachedProfile(walletId: String): LoyaltyProfileEntity? {
        return profiles[walletId] ?: profileCache.getCache(walletId)?.also { profiles.putIfAbsent(walletId, it) }
    }

    private fun loadProfile(walletId: String): LoyaltyProfileEntity? {
        return try {
            api.loyalty.getProfile(walletId).also { profile ->
                profiles[walletId] = profile
                profileCache.setCache(walletId, profile)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.w(e, "Loyalty profile is unavailable")
            null
        }
    }

    private companion object {
        private const val PROFILE_CACHE_PATH = "loyalty_profile"
        private const val PREFERENCES_NAME = "loyalty"
        private const val KEY_BANNER_HIDDEN_WALLETS = "banner_hidden_wallets"
        private const val KEY_REFERRAL_CODE = "referral_code"
        private const val PERPS_REWARD_KIND = "perps"
    }
}
