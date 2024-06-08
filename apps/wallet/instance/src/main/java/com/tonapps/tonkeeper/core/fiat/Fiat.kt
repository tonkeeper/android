package com.tonapps.tonkeeper.core.fiat

import android.app.Application
import com.tonapps.extensions.prefs
import com.tonapps.extensions.putBoolean
import com.tonapps.tonkeeper.core.fiat.models.FiatData
import com.tonapps.tonkeeper.core.fiat.models.FiatItem
import com.tonapps.tonkeeper.api.internal.repositories.FiatMethodsRepository
import com.tonapps.wallet.api.API
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.crypto.digest.sha512
import org.ton.crypto.hex
import java.util.UUID

@Deprecated("Need refactorings")
class Fiat(
    app: Application,
    private val fiatMethodsRepository: FiatMethodsRepository,
    private val api: API
) {

    private companion object {
        private const val SHOW_CONFIRMATION = "show_confirmation"
    }

    private val keyValue = app.prefs("fiat")

    suspend fun replaceUrl(
        url: String,
        address: String,
        currency: String
    ): String {
        var replacedUrl = url.replace("{ADDRESS}", address)
        replacedUrl = replacedUrl.replace("{CUR_FROM}", currency)
        replacedUrl = replacedUrl.replace("{CUR_TO}", "TON")

        if (replacedUrl.contains("TX_ID")) {
            val mercuryoSecret = api.config.mercuryoSecret
            val signature = hex(sha512((address+mercuryoSecret).toByteArray()))
            val tx = "mercuryo_" + UUID.randomUUID().toString()
            replacedUrl = replacedUrl.replace("{TX_ID}", tx)
            replacedUrl = replacedUrl.replace("=TON&", "=TONCOIN&")
            replacedUrl += "&signature=$signature"
        }
        return replacedUrl
    }

    suspend fun isShowConfirmation(
        id: String
    ): Boolean {
        val key = showConfirmationKey(id)
        return keyValue.getBoolean(key, true)
    }

    suspend fun disableShowConfirmation(
        id: String
    ) {
        val key = showConfirmationKey(id)
        keyValue.putBoolean(key, false)
    }

    private fun showConfirmationKey(id: String): String {
        return "$SHOW_CONFIRMATION-$id"
    }

    suspend fun init(
        countryCode: String
    ) = withContext(Dispatchers.IO) {
        fiatMethodsRepository.get(countryCode)
    }

    suspend fun getData(
        countryCode: String
    ): FiatData? = withContext(Dispatchers.IO) {
        fiatMethodsRepository.get(countryCode)
    }

    suspend fun getMethods(
        countryCode: String
    ): List<FiatItem> {
        val data = getData(countryCode) ?: return emptyList()
        val layout = data.layoutByCountry(countryCode)
        return data.getBuyItemsByMethods(layout.methods)
    }
}