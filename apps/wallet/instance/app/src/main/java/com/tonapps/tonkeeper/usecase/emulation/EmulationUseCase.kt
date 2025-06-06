package com.tonapps.tonkeeper.usecase.emulation

import com.tonapps.blockchain.ton.AndroidSecureRandom
import com.tonapps.icu.Coins
import com.tonapps.icu.Coins.Companion.sumOf
import com.tonapps.tonkeeper.extensions.isSafeModeEnabled
import com.tonapps.tonkeeper.manager.assets.AssetsManager
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.MessageBodyEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.core.entity.TransferType
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import io.tonapi.models.JettonQuantity
import io.tonapi.models.MessageConsequences
import io.tonapi.models.Risk
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import java.math.BigDecimal
import kotlin.math.abs

class EmulationUseCase(
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val batteryRepository: BatteryRepository,
    private val api: API,
    private val assetsManager: AssetsManager,
) {

    suspend operator fun invoke(
        message: MessageBodyEntity,
        useBattery: Boolean = false,
        forceRelayer: Boolean = false,
        params: Boolean = false,
    ): Emulated {
        return try {
            if (forceRelayer || useBattery) {
                emulateWithBattery(
                    message = message,
                    forceRelayer = forceRelayer,
                    params = params
                )
            } else {
                emulate(message, params)
            }
        } catch (e: Throwable) {
            Emulated(
                consequences = null,
                total = Emulated.Total(Coins.ZERO, 0, false),
                extra = Emulated.defaultExtra,
                currency = settingsRepository.currency,
                failed = true,
                type = TransferType.Default
            )
        }
    }

    private fun createMessage(
        message: MessageBodyEntity,
        internalMessage: Boolean
    ): Cell {
        return message.createSignedBody(
            privateKey = PrivateKeyEd25519(AndroidSecureRandom),
            internalMessage = internalMessage
        )
    }

    private suspend fun emulateWithBattery(
        message: MessageBodyEntity,
        forceRelayer: Boolean,
        params: Boolean,
    ): Emulated {
        try {
            if (api.config.isBatteryDisabled) {
                throw IllegalStateException("Battery is disabled")
            }

            val wallet = message.wallet
            val tonProofToken = accountRepository.requestTonProofToken(wallet) ?: throw IllegalStateException("Can't find TonProof token")
            val boc = createMessage(message, true)

            val (consequences, withBattery) = batteryRepository.emulate(
                tonProofToken = tonProofToken,
                publicKey = wallet.publicKey,
                testnet = wallet.testnet,
                boc = boc,
                forceRelayer = forceRelayer,
                safeModeEnabled = settingsRepository.isSafeModeEnabled(api)
            ) ?: throw IllegalStateException("Failed to emulate battery")

            return parseEmulated(wallet, consequences, TransferType.Battery)
        } catch (e: Throwable) {
            return emulate(message, params)
        }
    }

    private suspend fun emulate(message: MessageBodyEntity, params: Boolean): Emulated {
        val wallet = message.wallet
        val boc = createMessage(message, false)
        val consequences = (if (params) {
            api.emulate(
                cell = boc,
                testnet = wallet.testnet,
                address = wallet.address,
                balance = ((Coins.ONE + Coins.ONE) + calculateTransferAmount(message.transfers)).toLong(),
                safeModeEnabled = settingsRepository.isSafeModeEnabled(api)
            )
        } else {
            api.emulate(
                cell = boc,
                testnet = wallet.testnet,
                safeModeEnabled = settingsRepository.isSafeModeEnabled(api)
            )
        }) ?: throw IllegalArgumentException("Emulation failed")
        return parseEmulated(wallet, consequences, TransferType.Default)
    }

    private suspend fun parseEmulated(
        wallet: WalletEntity,
        consequences: MessageConsequences,
        transferType: TransferType,
        currency: WalletCurrency = settingsRepository.currency,
    ): Emulated {
        val total = getTotal(wallet, consequences.risk, currency)
        val extra = getExtra(consequences.event.extra, currency)
        return Emulated(
            consequences = consequences,
            type = transferType,
            total = total,
            extra = extra,
            currency = currency,
        )
    }

    private suspend fun getTotal(
        wallet: WalletEntity,
        risk: Risk,
        currency: WalletCurrency,
    ): Emulated.Total {
        val balanceFiat = assetsManager.getTotalBalance(wallet, currency) ?: Coins.ZERO
        val tokens = getTokens(wallet, risk.ton, risk.jettons)
        val rates = ratesRepository.getRates(currency, tokens.map { it.token.address })
        val totalFiat = tokens.map { token ->
            rates.convert(token.token.address, token.value)
        }.sumOf { it }

        val diff = if (balanceFiat > Coins.ZERO) {
            totalFiat.value / balanceFiat.value
        } else {
            totalFiat.value
        }

        return Emulated.Total(
            totalFiat = totalFiat,
            nftCount = risk.nfts.size,
            isDangerous = diff >= BigDecimal("0.2")
        )
    }

    private suspend fun getExtra(
        extra: Long,
        currency: WalletCurrency,
    ): Emulated.Extra {
        val value = Coins.of(abs(extra))
        val rates = ratesRepository.getTONRates(currency)
        val fiat = rates.convertTON(value)

        return Emulated.Extra(
            isRefund = extra >= 0,
            value = value,
            fiat = fiat,
        )
    }

    private fun getTokens(
        wallet: WalletEntity,
        tonValue: Long,
        jettons: List<JettonQuantity>
    ): List<BalanceEntity> {
        val list = mutableListOf<BalanceEntity>()
        list.add(BalanceEntity.create(
            accountId = wallet.address,
            value = Coins.of(tonValue),
        ))
        for (jettonQuantity in jettons) {
            val token = TokenEntity(jettonQuantity.jetton)
            val value = Coins.ofNano(jettonQuantity.quantity, token.decimals)
            list.add(BalanceEntity(
                token = token,
                value = value,
                walletAddress = jettonQuantity.walletAddress.address
            ))
        }
        return list.toList()
    }

    companion object {

        fun calculateTransferAmount(transfers: List<WalletTransfer>): Coins {
            return transfers.sumOf {
                Coins.of(it.coins.coins.amount.toLong())
            }
        }
    }
}
