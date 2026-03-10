package com.tonapps.tonkeeper.ui.screen.onramp.main.state

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.tonapps.tonkeeper.extensions.capitalized
import com.tonapps.wallet.data.purchase.entity.OnRamp
import com.tonapps.wallet.localization.Localization

data class OnRampPaymentMethodState(
    val selectedType: String? = null,
    val methods: List<Method>
) {

    data class Method(
        val type: String,
        val title: String,
        val subtitle: CharSequence?,
        val icon: Uri,
        val country: String
    ) {

        val isCard: Boolean by lazy {
            type.equals(CARD_TYPE, true)
        }
    }

    companion object {

        const val CARD_TYPE = "card"

        val sortKeys = listOf(
            "card",
            "google_pay",
            "revolut",
            "paypal"
        )

        fun createMethod(
            context: Context,
            data: OnRamp.Method,
            country: String
        ): Method {
            val title = createTitle(context, data.type)

            return Method(
                type = data.type,
                title = title,
                subtitle = getSubtitleByType(context, data.type, country),
                icon = data.image.toUri(),
                country = country
            )
        }

        private fun getSubtitleByType(
            context: Context,
            type: String,
            country: String
        ): CharSequence? {
            if (!type.equals("card", true)) {
                return null
            }
            if (country.equals("ru", true)) {
                return "МИР"
            }
            return context.getString(Localization.credit_card_subtitle)
        }

        private fun getTitleResByType(type: String): Int? {
            return when (type) {
                "card" -> Localization.credit_card
                "revolut" -> Localization.revolut
                "google_pay" -> Localization.google_pay
                "paypal" -> Localization.paypal
                else -> null
            }
        }

        private fun defaultTitle(type: String): String {
            return type.replace("_", " ").capitalized
        }

        private fun createTitle(context: Context, type: String) = getTitleResByType(type)?.let {
            context.getString(it)
        } ?: defaultTitle(type)
    }
}