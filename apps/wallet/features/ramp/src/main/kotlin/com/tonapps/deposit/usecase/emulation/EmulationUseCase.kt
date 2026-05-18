package com.tonapps.deposit.usecase.emulation

import com.tonapps.blockchain.ton.AndroidSecureRandom
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.icu.Coins
import com.tonapps.icu.Coins.Companion.sumOf
import com.tonapps.wallet.api.API
import com.tonapps.blockchain.model.legacy.BalanceEntity
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.wallet.api.tron.TronApi
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.MessageBodyEntity
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.wallet.api.extensions.toTokenEntity
import com.tonapps.wallet.data.core.entity.TransferType
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import io.tonapi.models.JettonQuantity
import io.tonapi.models.MessageConsequences
import io.tonapi.models.Risk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

class EmulationUseCase(
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val batteryRepository: BatteryRepository,
    private val api: API,
    private val tokenRepository: TokenRepository,
    private val delegate: Delegate,
) {

    interface Delegate { // TODO remove
        suspend fun getTotalBalance(wallet: WalletEntity, currency: WalletCurrency): Coins?
    }

    private val contractExecution = EmulationContractExecution(api)

    suspend operator fun invoke(
        message: MessageBodyEntity,
        useBattery: Boolean = false,
        forceRelayer: Boolean = false,
        checkTonBalance: Boolean = false,
        params: Boolean = false,
    ): Emulated {
        return try {
            if (forceRelayer || useBattery) {
                emulateWithBattery(
                    message = message,
                    forceRelayer = forceRelayer,
                )
            } else {
                emulate(message, params, checkTonBalance)
            }
        } catch (e: Throwable) {
            Emulated(
                consequences = null,
                total = Emulated.Total(Coins.ZERO, 0, false),
                extra = Emulated.defaultExtra,
                currency = settingsRepository.currency,
                failed = true,
                type = TransferType.Default,
                error = e,
            )
        }
    }

    suspend operator fun invoke(
        wallet: WalletEntity,
        seqNo: Int,
        unsignedBody: Cell,
        outMsgs: List<Cell>,
        forwardAmount: Coins,
    ): Emulated {
        return try {
            emulate(wallet, seqNo, unsignedBody, outMsgs, forwardAmount)
        } catch (e: Throwable) {
            Emulated(
                consequences = null,
                total = Emulated.Total(Coins.ZERO, 0, false),
                extra = Emulated.defaultExtra,
                currency = settingsRepository.currency,
                failed = true,
                type = TransferType.Default,
                error = e,
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
    ): Emulated {
        if (api.getConfig(message.wallet.network).batterySendDisabled) {
            throw IllegalStateException("Battery is disabled")
        }

        val wallet = message.wallet
        val tonProofToken = accountRepository.requestTonProofToken(wallet)
            ?: throw IllegalStateException("Can't find TonProof token")
        val boc = createMessage(message, true)

        val result = batteryRepository.emulate(
            tonProofToken = tonProofToken,
            publicKey = wallet.publicKey,
            network = wallet.network,
            boc = boc,
            forceRelayer = forceRelayer,
            safeModeEnabled = settingsRepository.isSafeModeEnabled(wallet.network)
        ) ?: throw IllegalStateException("Failed to emulate battery")

        return parseEmulated(wallet, result.consequences, TransferType.Battery)
    }

    private suspend fun emulate(
        wallet: WalletEntity,
        seqNo: Int,
        unsignedBody: Cell,
        outMsgs: List<Cell>,
        forwardAmount: Coins,
    ): Emulated {
        val signedBoc = wallet.sign(
            privateKey = PrivateKeyEd25519(AndroidSecureRandom),
            seqNo = seqNo,
            body = unsignedBody
        )

        val account = api.accounts(wallet.network).getAccount(wallet.accountId)
        val accountBalance = Coins.of(account.balance)
        val totalFee = contractExecution.computeRemoveExtensionFee(wallet, signedBoc, outMsgs)
        val totalAmount =
            totalFee + forwardAmount
        if (totalAmount > accountBalance) {
            throw InsufficientBalanceError(accountBalance, totalAmount)
        }

        val consequences = api.emulate(
            cell = signedBoc,
            network = wallet.network,
            safeModeEnabled = settingsRepository.isSafeModeEnabled(wallet.network)
        ) ?: throw IllegalArgumentException("Emulation failed")
        return parseEmulated(wallet, consequences, TransferType.Default)
    }

    private suspend fun emulate(
        message: MessageBodyEntity,
        params: Boolean,
        checkTonBalance: Boolean
    ): Emulated {
        val wallet = message.wallet
        val boc = createMessage(message, false)

        if (checkTonBalance) {
            val account = api.accounts(wallet.network).getAccount(wallet.accountId)
            val accountBalance = Coins.of(account.balance)
            val totalFee = contractExecution.computeFee(wallet, account, boc, message.getOutMsgs())
            val totalAmount =
                totalFee + message.transfers.sumOf { Coins.of(it.coins.coins.toString()) }
            if (totalAmount > accountBalance) {
                throw InsufficientBalanceError(accountBalance, totalAmount)
            }
        }

        val consequences = (if (params) {
            api.emulate(
                cell = boc,
                network = wallet.network,
                address = wallet.address,
                balance = ((Coins.ONE + Coins.ONE) + calculateTransferAmount(message.transfers)).toLong(),
                safeModeEnabled = settingsRepository.isSafeModeEnabled(wallet.network)
            )
        } else {
            api.emulate(
                cell = boc,
                network = wallet.network,
                safeModeEnabled = settingsRepository.isSafeModeEnabled(wallet.network)
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
        val extra = getExtra(wallet.network, consequences.event.extra, currency)
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
        val balanceFiat = delegate.getTotalBalance(wallet, currency) ?: Coins.ZERO
        val ton = tokenRepository.getTON(currency, wallet.accountId, wallet.network, true)
        val tonValue = if (risk.transferAllRemainingBalance) {
            ton?.balance?.value?.toLong() ?: risk.ton
        } else {
            risk.ton
        }
        val tokens = getTokens(wallet, tonValue, risk.jettons)
        val rates = ratesRepository.getRates(wallet.network, currency, tokens.map { it.token.address })
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
        network: TonNetwork,
        extra: Long,
        currency: WalletCurrency,
    ): Emulated.Extra {
        val value = Coins.of(abs(extra))
        val rates = ratesRepository.getTONRates(network, currency)
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
        list.add(
            BalanceEntity.create(
                accountId = wallet.address,
                value = Coins.of(tonValue),
            )
        )
        for (jettonQuantity in jettons) {
            val token = jettonQuantity.jetton.toTokenEntity()
            val value = Coins.ofNano(jettonQuantity.quantity, token.decimals)
            list.add(
                BalanceEntity(
                    token = token,
                    value = value,
                    walletAddress = jettonQuantity.walletAddress.address
                )
            )
        }
        return list.toList()
    }

    private suspend fun getBatteryCharges(wallet: WalletEntity): Int = withContext(Dispatchers.IO) {
        accountRepository.requestTonProofToken(wallet)?.let {
            batteryRepository.getCharges(it, wallet.publicKey, wallet.network)
        } ?: 0
    }

    suspend fun getTrc20TransferDefaultFees(
        wallet: WalletEntity,
        currency: WalletCurrency,
        emulation: TronFeesEmulation? = null,
    ): Trc20TransferDefaultFees {
        val config = batteryRepository.getConfig(wallet.network)
        val tokens = tokenRepository.get(currency, wallet.accountId, wallet.network)
        val tonBalance = tokens?.find { it.isTon }?.balance?.value ?: Coins.ZERO
        val trxBalance = tokens?.find { it.isTrx }?.balance?.value ?: Coins.ZERO
        val chargesBalance = getBatteryCharges(wallet)

        val tonAmount = emulation?.ton ?: Coins.of(config.meanPrices.tonMeanPriceTronUsdt.toBigDecimal())
        val tonFiat = ratesRepository.getTONRates(wallet.network, currency)
            .convertTON(tonAmount)
        val tonAvailableTransfers = tonBalance.divide(tonAmount, RoundingMode.FLOOR).value.toInt()

        val charges = emulation?.batteryCharges ?: config.meanPrices.batteryMeanPriceTronUsdt
        val chargesTon = config.chargeCost.toBigDecimal().multiply(charges.toBigDecimal())
        val chargesFiat = ratesRepository.getTONRates(wallet.network, currency)
            .convertTON(Coins.of(chargesTon))
        val chargesAvailableTransfers = chargesBalance / charges

        val trxFee = emulation?.trx ?: getCachedTrxFee(api.tron)
        val trxFiat = ratesRepository.getRates(wallet.network, currency, TokenEntity.TRX.address)
            .convert(TokenEntity.TRX.address, trxFee)
        val trxAvailableTransfers = trxBalance.divide(trxFee, RoundingMode.FLOOR).value.toInt()

        val result = Trc20TransferDefaultFees(
            totalAvailableTransfers = chargesAvailableTransfers + tonAvailableTransfers + trxAvailableTransfers,
            currency = currency,
            batteryFee = Trc20TransferDefaultFees.BatteryFee(
                balance = chargesBalance,
                charges = charges,
                fiatAmount = chargesFiat,
                availableTransfers = chargesAvailableTransfers
            ),
            tonFee = Trc20TransferDefaultFees.TonFee(
                balance = tonBalance,
                amount = tonAmount,
                fiatAmount = tonFiat,
                availableTransfers = tonAvailableTransfers
            ),
            trxFee = Trc20TransferDefaultFees.TrxFee(
                balance = trxBalance,
                amount = trxFee,
                fiatAmount = trxFiat,
                availableTransfers = trxAvailableTransfers
            ),
        )
        return result
    }

    private suspend fun getCachedTrxFee(api: TronApi): Coins {
        val now = System.currentTimeMillis()
        synchronized(cacheLock) {
            val cached = cachedTrxFee
            if (cached != null && now - cachedTrxFeeTimestamp < TRX_FEE_TTL_MS) {
                return cached
            }
        }

        val newValue = api.getBurnTrxAmountForResources(api.transferDefaultResources)
        synchronized(cacheLock) {
            cachedTrxFee = newValue
            cachedTrxFeeTimestamp = now
        }

        return newValue
    }

    private fun calculateTransferAmount(transfers: List<WalletTransfer>): Coins {
        return transfers.sumOf {
            Coins.of(it.coins.coins.amount.toLong())
        }
    }

    companion object {
        private const val TRX_FEE_TTL_MS = 10 * 60 * 1000L
        private val cacheLock = Any()

        @Volatile
        private var cachedTrxFee: Coins? = null
        @Volatile
        private var cachedTrxFeeTimestamp: Long = 0L

    }
}
