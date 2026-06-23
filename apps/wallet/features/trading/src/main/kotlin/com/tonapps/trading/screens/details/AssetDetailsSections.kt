package com.tonapps.trading.screens.details

import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.icu.Formatter
import com.tonapps.trading.asTokenEntity
import com.tonapps.trading.iconRes
import com.tonapps.wallet.data.events.tx.model.TxEvent
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.localization.Localization
import io.tradingapi.models.AssetDetailsResponse
import ui.components.events.UiEvent
import io.tradingapi.models.SectionLinks
import io.tradingapi.models.SectionTradingActivity
import java.math.BigDecimal

data class AssetDetailsSections(
    val balance: Balance?,
    val about: About?,
    val overview: Overview?,
    val trading: Trading?,
    val recentEvents: RecentEvents?,
    val links: Links?,
) {
    data class Balance(
        val token: TokenEntity,
        val balanceFormatted: String,
        val fiatFormatted: String,
    )

    data class About(
        val body: String,
    )

    data class Overview(
        val items: List<OverviewItem>,
    )

    data class OverviewItem(
        val titleRes: Int,
        val tooltipTextRes: Int,
        val valueFormatted: String,
    )

    data class Trading(
        val volume24hFormatted: String,
        val volumeChange24hFormatted: String?,
        val buyWeight: Float,
        val sellWeight: Float,
        val buy24hFormatted: String,
        val sell24hFormatted: String,
    )

    data class RecentEvents(
        val items: List<Item>,
        val showSeeAll: Boolean,
        val hiddenBalances: Boolean,
        val token: TokenEntity,
    ) {
        data class Item(
            val txEvent: TxEvent,
            val uiEvent: UiEvent.Item,
        )
    }

    data class Links(
        val items: List<LinkItem>,
    )

    data class LinkItem(
        val name: String,
        val iconRes: Int,
        val url: String,
    )

    companion object {

        fun build(
            details: AssetDetailsResponse,
            currencyCode: String,
            hiddenBalances: Boolean,
            accountToken: AccountTokenEntity?,
            recentEvents: List<RecentEvents.Item>?,
        ): AssetDetailsSections {
            return AssetDetailsSections(
                balance = buildBalance(accountToken),
                about = buildAbout(details),
                overview = buildOverview(details, currencyCode),
                trading = buildTrading(details.sections.tradingActivity, currencyCode),
                recentEvents = buildRecentEvents(details, hiddenBalances, recentEvents),
                links = buildLinks(details.sections.links),
            )
        }

        private fun buildBalance(
            accountToken: AccountTokenEntity?,
        ): Balance? {
            val token = accountToken ?: return null
            return Balance(
                token = token.token,
                balanceFormatted = CurrencyFormatter.format(
                    currency = token.token.symbol,
                    value = token.balance.value,
                ).toString(),
                fiatFormatted = CurrencyFormatter.formatFiat(
                    currency = token.fiatCurrency.code,
                    value = token.fiat,
                ).toString(),
            )
        }

        private fun buildAbout(details: AssetDetailsResponse): About? {
            val about = details.sections.about
            val text = about.text?.trim() ?: return null
            if (!about.enabled || text.isBlank()) return null
            return About(body = text)
        }

        private fun buildOverview(
            details: AssetDetailsResponse,
            currencyCode: String,
        ): Overview? {
            val overview = details.sections.overview
            if (!overview.enabled) return null
            val decimals = details.asset.decimals
            val marketCapFormatted = overview.marketCap?.let {
                CurrencyFormatter.formatFiat(
                    currency = currencyCode,
                    value = BigDecimal(it),
                    compact = true,
                ).toString()
            }
            val totalSupplyFormatted = overview.totalSupply?.let {
                CurrencyFormatter.format(
                    currency = "",
                    value = Coins.ofNano(it, decimals),
                    compact = true,
                ).toString()
            }
            val circulatingSupplyFormatted = overview.circulatingSupply?.let {
                CurrencyFormatter.format(
                    currency = "",
                    value = Coins.ofNano(it, decimals),
                    compact = true,
                ).toString()
            }
            val items = buildList {
                marketCapFormatted?.let {
                    add(
                        OverviewItem(
                            titleRes = Localization.market_cap,
                            tooltipTextRes = Localization.market_cap_tooltip,
                            valueFormatted = it,
                        ),
                    )
                }
                totalSupplyFormatted?.let {
                    add(
                        OverviewItem(
                            titleRes = Localization.total_supply,
                            tooltipTextRes = Localization.total_supply_tooltip,
                            valueFormatted = it,
                        ),
                    )
                }
                circulatingSupplyFormatted?.let {
                    add(
                        OverviewItem(
                            titleRes = Localization.circulating_supply,
                            tooltipTextRes = Localization.circulating_supply_tooltip,
                            valueFormatted = it,
                        ),
                    )
                }
            }
            if (items.isEmpty()) return null
            return Overview(items = items)
        }

        private fun buildTrading(
            tradingActivity: SectionTradingActivity,
            currencyCode: String,
        ): Trading? {
            if (!tradingActivity.enabled) return null
            val totalValue = tradingActivity.volume24h?.let { BigDecimal(it) } ?: return null
            val buyValue = tradingActivity.buy24h?.let { BigDecimal(it) } ?: return null
            val sellValue = tradingActivity.sell24h?.let { BigDecimal(it) } ?: return null
            if (totalValue.compareTo(BigDecimal.ZERO) == 0) return null

            val buyWeight = (buyValue / totalValue).toFloat().coerceIn(0f, 1f)
            val sellWeight = (sellValue / totalValue).toFloat().coerceIn(0f, 1f)

            val volume24hFormatted = CurrencyFormatter.formatFiat(
                currency = currencyCode,
                value = totalValue,
                compact = true,
            ).toString()
            val buy24hFormatted = CurrencyFormatter.formatFiat(
                currency = currencyCode,
                value = buyValue,
                compact = true,
            ).toString()
            val sell24hFormatted = CurrencyFormatter.formatFiat(
                currency = currencyCode,
                value = sellValue,
                compact = true,
            ).toString()

            val volumeChange24hFormatted =
                tradingActivity.volumeChange24h?.let { Formatter.percent(it) }

            return Trading(
                volume24hFormatted = volume24hFormatted,
                volumeChange24hFormatted = volumeChange24hFormatted,
                buyWeight = buyWeight,
                sellWeight = sellWeight,
                buy24hFormatted = buy24hFormatted,
                sell24hFormatted = sell24hFormatted,
            )
        }

        private fun buildLinks(sectionLinks: SectionLinks): Links? {
            val items = sectionLinks.items ?: return null
            if (!sectionLinks.enabled || items.isEmpty()) return null
            return Links(
                items = items.map { item ->
                    LinkItem(
                        name = item.name,
                        iconRes = item.type.iconRes(),
                        url = item.url,
                    )
                },
            )
        }

        private fun buildRecentEvents(
            details: AssetDetailsResponse,
            hiddenBalances: Boolean,
            recentEvents: List<RecentEvents.Item>?,
        ): RecentEvents? {
            if (recentEvents == null || recentEvents.isEmpty()) return null
            val token = details.asset.asTokenEntity
            return RecentEvents(
                items = recentEvents.take(3),
                showSeeAll = recentEvents.size > 3,
                hiddenBalances = hiddenBalances,
                token = token,
            )
        }
    }
}
