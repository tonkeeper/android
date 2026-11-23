package com.tonapps.perps.funding

import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.blockchain.tron.toEvmHex
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.deposit.multicoin.screens.confirm.fee.HermesSelfCall
import com.tonapps.perps.security.PerpsRelayRouter
import com.tonapps.perps.security.PerpsSecurityConfig
import com.tonapps.wallet.data.multichain.lighter.PerpetualDepositProvider
import com.tonapps.wallet.data.multichain.lighter.PerpsQuoteError
import java.math.BigInteger

private const val CHAIN_PAYMENT = "chain payment"
private const val HEX_PREFIX = "0x"
private const val HEX_RADIX = 16
private const val SELECTOR_HEX_LENGTH = 8
private const val WORD_HEX_LENGTH = 64
private const val ADDRESS_HEX_LENGTH = 40
private const val APPROVE = "095ea7b3"
private const val TRANSFER = "a9059cbb"
private const val DEPOSIT_ERC20 = "e8017952"
private const val DEPOSIT_NATIVE = "49290c1c"
private const val GATEWAY_DEPOSIT = "8a857083"
private const val TRANSFER_AND_MULTICALL = "f9e4bab4"
private const val GATEWAY_USDC_ASSET_INDEX = 3L
private const val GATEWAY_PERPS_ROUTE = 0L
private const val APPROVE_WORDS = 2
private const val TRANSFER_WORDS = 2
private const val DEPOSIT_ERC20_WORDS = 4
private const val DEPOSIT_NATIVE_WORDS = 2
private const val GATEWAY_DEPOSIT_WORDS = 4
private const val TRANSFER_AND_MULTICALL_HEAD_WORDS = 6
private const val TRANSFERRED_TOKENS_ARGUMENT = 0
private const val TRANSFERRED_AMOUNTS_ARGUMENT = 1
private val ZERO_ADDRESS = "0".repeat(ADDRESS_HEX_LENGTH)
private val BRIDGE_SOURCES = setOf(
    PerpsFundingAssetId.USDC_ETHEREUM,
    "arb/mainnet/erc20/0xaf88d065e77c8cc2239327c5edb3a432268e5831",
    "base/mainnet/erc20/0x833589fcd6edb6e08f4c7c32d4f71b54bda02913",
    "polygon/mainnet/erc20/0x3c499c542cef5e3811e1192ce70d8cc03d5c3359",
)
private val HEX_DIGITS = Regex("([0-9a-f]{2})*")
private val WORD_BYTES: BigInteger = BigInteger.valueOf(32L)
private val BPS: BigInteger = BigInteger.valueOf(10_000L)
private val MAX_SHORTFALL_BPS: BigInteger = BigInteger.valueOf(300L)

internal fun fitsDepositBudget(sent: BigInteger, amountUnits: BigInteger): Boolean {
    return sent.signum() > 0 && sent <= amountUnits && sent * BPS >= amountUnits * (BPS - MAX_SHORTFALL_BPS)
}

internal object PerpsChainDepositReader {

    fun read(
        source: PerpsDepositSource,
        provider: PerpetualDepositProvider,
        payment: Operation,
        amountUnits: BigInteger,
        beneficiary: String,
        registration: PerpsKeyRegistration?,
        security: PerpsSecurityConfig?,
    ): ConfirmType.PerpetualDeposit.VerifiedPayment? {
        val chain = source.account.value.chain
        if (payment.payloads.isEmpty() || payment.payloads.any { it.type != Payload.Type.Main || it.chain != chain || it.version != Payload.VERSION }) {
            refuse("payloads")
        }
        val sent = when {
            chain is Chain.Tron -> readTron(source, provider, payment, amountUnits, security)
            chain.isEvm -> readEvm(source, provider, payment, amountUnits, beneficiary, registration, security)
            else -> refuse("chain")
        } ?: return null
        return ConfirmType.PerpetualDeposit.VerifiedPayment(provider = RELAY_PROVIDER, amount = sent.toChain())
    }

    private fun readEvm(
        source: PerpsDepositSource,
        provider: PerpetualDepositProvider,
        payment: Operation,
        amountUnits: BigInteger,
        beneficiary: String,
        registration: PerpsKeyRegistration?,
        security: PerpsSecurityConfig?,
    ): BigInteger? {
        val sender = source.account.data.displayAddress.lowercase()
        val calls = payment.payloads.flatMap { evmCalls(it, source.account.value.chain, sender, security) }
        return when (provider) {
            PerpetualDepositProvider.Direct -> {
                readDirect(source, calls, amountUnits, beneficiary, registration, security)
                null
            }

            PerpetualDepositProvider.FunXyz -> {
                readBridgeTransfer(source, payment, calls, amountUnits, sender)
                null
            }

            PerpetualDepositProvider.Relay -> readRelay(source, calls, amountUnits, sender, security)

            PerpetualDepositProvider.Omniston -> refuse("provider")
        }
    }

    private fun evmCalls(payload: Payload, chain: Chain, sender: String, security: PerpsSecurityConfig?): List<Call> {
        val meta = payload.meta as? PayloadMeta.Evm.Main ?: refuse("payload")
        meta.authorization?.let { authorization ->
            PerpsKeyRegistrationReader.readPaymentDelegation(chain, authorization, security ?: refuse("authorization"))
        }
        val to = meta.to.display.lowercase()
        if (!HermesSelfCall.isSelfCall(sender, to)) {
            return listOf(Call(to, meta.value.toJava(), hexDigits(meta.data)))
        }
        val calls = HermesSelfCall.unbatch(sender, meta.value, meta.data, chain) ?: refuse("batch")
        return calls.map { call ->
            val target = call.target.display.lowercase()
            if (target == security?.hermesDelegate) {
                refuse("batch")
            }
            Call(target, call.value.toJava(), hexDigits(call.data))
        }
    }

    private fun readDirect(
        source: PerpsDepositSource,
        calls: List<Call>,
        amountUnits: BigInteger,
        beneficiary: String,
        registration: PerpsKeyRegistration?,
        security: PerpsSecurityConfig?,
    ) {
        if (!source.isBestValue) {
            refuse("direct source")
        }
        val gateway = security?.lighterGateway ?: refuse("lighter gateway")
        val token = tokenAddress(source)
        val approved = calls.firstOrNull()?.takeIf { it.selector == APPROVE }
        approved?.let { readApprove(it, token, gateway, amountUnits) }
        val rest = if (approved == null) {
            calls
        } else {
            calls.drop(1)
        }
        val deposit = rest.firstOrNull() ?: refuse("gateway deposit")
        val expected = GATEWAY_DEPOSIT + word(beneficiary) + word(BigInteger.valueOf(GATEWAY_USDC_ASSET_INDEX)) +
            word(BigInteger.valueOf(GATEWAY_PERPS_ROUTE)) + word(amountUnits)
        if (deposit.target != gateway || deposit.value.signum() != 0 || deposit.data != expected) {
            refuse("gateway deposit")
        }
        val keyCalls = rest.drop(1)
        if (keyCalls.isEmpty()) {
            return
        }
        val key = keyCalls.singleOrNull() ?: refuse("calls")
        if (registration == null || security == null ||
            key.target != security.changePubKeyContract ||
            key.value.signum() != 0 ||
            key.data != hexDigits(registration.calldata)
        ) {
            refuse("key registration")
        }
    }

    private fun readBridgeTransfer(
        source: PerpsDepositSource,
        payment: Operation,
        calls: List<Call>,
        amountUnits: BigInteger,
        sender: String,
    ) {
        if (source.fundingAsset.raw.lowercase() !in BRIDGE_SOURCES) {
            refuse("bridge source")
        }
        val meta = payment.payloads.singleOrNull()?.meta as? PayloadMeta.Evm.Main ?: refuse("bridge transfer")
        val transfer = calls.singleOrNull() ?: refuse("bridge transfer")
        val token = tokenAddress(source)
        val words = transfer.arguments(TRANSFER, TRANSFER_WORDS) ?: refuse("bridge transfer")
        val recipient = address(words[0]) ?: refuse("bridge transfer")
        if (meta.authorization != null ||
            transfer.target != token ||
            transfer.value.signum() != 0 ||
            recipient == sender ||
            recipient == token ||
            recipient == HEX_PREFIX + ZERO_ADDRESS ||
            uint(words[1]) != amountUnits
        ) {
            refuse("bridge transfer")
        }
    }

    private fun readRelay(
        source: PerpsDepositSource,
        calls: List<Call>,
        amountUnits: BigInteger,
        sender: String,
        security: PerpsSecurityConfig?,
    ): BigInteger {
        if (source.isBestValue) {
            refuse("relay source")
        }
        val chainKey = source.account.value.chain.network.type.id
        val depositories = security?.evmRelayDepositories?.get(chainKey).orEmpty()
        val routers = security?.evmRelayRouters?.get(chainKey).orEmpty()
        val payment = calls.last()
        val approval = when (calls.size) {
            1 -> null
            2 -> calls.first()
            else -> refuse("calls")
        }
        val router = routers[payment.target]
        if (source.fundingAsset.isCoin) {
            if (approval != null) {
                refuse("calls")
            }
            when {
                payment.target in depositories -> readNativeDeposit(payment.data, sender)
                router == PerpsRelayRouter.Erc20Router -> Unit
                else -> refuse("relay target")
            }
            if (!fitsDepositBudget(payment.value, amountUnits)) {
                refuse("value")
            }
            return payment.value
        }
        val token = tokenAddress(source)
        if (payment.value.signum() != 0 || payment.target == token) {
            refuse("relay payment")
        }
        val allowance = approval?.let { readApprove(it, token, payment.target, amountUnits) }
        if (payment.target in depositories) {
            return readTokenDeposit(payment.data, sender, token, amountUnits)
        }
        return when (router) {
            PerpsRelayRouter.ApprovalProxy -> readApprovalProxyTransfer(payment.data, token, amountUnits, allowance)
            PerpsRelayRouter.Erc20Router, PerpsRelayRouter.RelayReceiver -> allowance ?: refuse("approve")
            null -> refuse("relay target")
        }
    }

    private fun readTron(
        source: PerpsDepositSource,
        provider: PerpetualDepositProvider,
        payment: Operation,
        amountUnits: BigInteger,
        security: PerpsSecurityConfig?,
    ): BigInteger {
        if (provider != PerpetualDepositProvider.Relay) {
            refuse("provider")
        }
        val depositories = security?.tronRelayDepositories.orEmpty()
        if (depositories.isEmpty()) {
            refuse("relay depository")
        }
        val calls = payment.payloads.map { payload ->
            val meta = payload.meta as? PayloadMeta.Tron.Main ?: refuse("payload")
            if (!meta.memo.isNullOrEmpty()) {
                refuse("memo")
            }
            Call(meta.to.display, meta.value.toJava(), hexDigits(meta.data))
        }
        val payment = calls.last()
        if (payment.target !in depositories) {
            refuse("relay target")
        }
        val approval = when (calls.size) {
            1 -> null
            2 -> calls.first()
            else -> refuse("calls")
        }
        val sender = tronAddressHex(source.account.data.displayAddress)
        if (source.fundingAsset.isCoin) {
            if (approval != null) {
                refuse("calls")
            }
            readNativeDeposit(payment.data, sender)
            if (!fitsDepositBudget(payment.value, amountUnits)) {
                refuse("value")
            }
            return payment.value
        }
        val token = source.fundingAsset.tokenAddress ?: refuse("token")
        if (payment.value.signum() != 0) {
            refuse("relay payment")
        }
        approval?.let { readApprove(it, token, tronAddressHex(payment.target), amountUnits) }
        return readTokenDeposit(payment.data, sender, tronAddressHex(token), amountUnits)
    }

    private fun readApprove(call: Call, token: String, spender: String, amountUnits: BigInteger): BigInteger {
        val words = call.arguments(APPROVE, APPROVE_WORDS) ?: refuse("approve")
        val allowance = uint(words[1])
        if (call.target != token || call.value.signum() != 0 || address(words[0]) != spender || allowance > amountUnits) {
            refuse("approve")
        }
        return allowance
    }

    private fun readNativeDeposit(data: String, depositor: String) {
        val words = Call.arguments(data, DEPOSIT_NATIVE, DEPOSIT_NATIVE_WORDS) ?: refuse("deposit")
        if (address(words[0]) != depositor) {
            refuse("depositor")
        }
    }

    private fun readTokenDeposit(data: String, depositor: String, token: String, amountUnits: BigInteger): BigInteger {
        val words = Call.arguments(data, DEPOSIT_ERC20, DEPOSIT_ERC20_WORDS) ?: refuse("deposit")
        val sent = uint(words[2])
        when {
            address(words[0]) != depositor -> refuse("depositor")
            address(words[1]) != token -> refuse("token")
            !fitsDepositBudget(sent, amountUnits) -> refuse("amount")
        }
        return sent
    }

    private fun readApprovalProxyTransfer(data: String, token: String, amountUnits: BigInteger, allowance: BigInteger?): BigInteger {
        val (tokens, amounts) = approvalProxyTransfers(data)
        val transferred = amounts.singleOrNull()?.let(::uint)
        if (tokens.singleOrNull()?.let(::address) != token ||
            transferred == null ||
            !fitsDepositBudget(transferred, amountUnits) ||
            (allowance != null && allowance < transferred)
        ) {
            refuse("approval proxy transfer")
        }
        return transferred
    }

    private fun approvalProxyTransfers(data: String): Pair<List<String>, List<String>> {
        val words = data.takeIf { it.startsWith(TRANSFER_AND_MULTICALL) }
            ?.drop(SELECTOR_HEX_LENGTH)
            ?.takeIf { it.length % WORD_HEX_LENGTH == 0 }
            ?.chunked(WORD_HEX_LENGTH)
            ?.takeIf { it.size >= TRANSFER_AND_MULTICALL_HEAD_WORDS }
            ?: refuse("approval proxy call")
        val tokens = arrayArgument(words, TRANSFERRED_TOKENS_ARGUMENT) ?: refuse("approval proxy call")
        val amounts = arrayArgument(words, TRANSFERRED_AMOUNTS_ARGUMENT) ?: refuse("approval proxy call")
        return tokens to amounts
    }

    private fun arrayArgument(words: List<String>, argument: Int): List<String>? {
        val offset = uint(words[argument])
        val (start, misalignment) = offset.divideAndRemainder(WORD_BYTES)
        if (misalignment.signum() != 0 || start >= BigInteger.valueOf(words.size.toLong())) {
            return null
        }
        val lengthIndex = start.toInt()
        val length = uint(words[lengthIndex])
        if (length > BigInteger.valueOf((words.size - lengthIndex - 1).toLong())) {
            return null
        }
        return words.subList(lengthIndex + 1, lengthIndex + 1 + length.toInt())
    }

    private fun tokenAddress(source: PerpsDepositSource): String {
        return source.fundingAsset.tokenAddress?.lowercase() ?: refuse("token")
    }

    private fun tronAddressHex(address: String): String {
        return try {
            HEX_PREFIX + address.toEvmHex().lowercase()
        } catch (e: IllegalArgumentException) {
            refuse("tron address")
        }
    }

    private fun address(word: String): String? {
        return word.takeIf { it.startsWith("0".repeat(WORD_HEX_LENGTH - ADDRESS_HEX_LENGTH)) }
            ?.let { HEX_PREFIX + it.takeLast(ADDRESS_HEX_LENGTH) }
    }

    private fun uint(word: String): BigInteger = BigInteger(word, HEX_RADIX)

    private fun word(address: String): String = address.lowercase().removePrefix(HEX_PREFIX).padStart(WORD_HEX_LENGTH, '0')

    private fun word(value: BigInteger): String = value.toString(HEX_RADIX).padStart(WORD_HEX_LENGTH, '0')

    private fun hexDigits(data: String?): String {
        val digits = data.orEmpty().removePrefix(HEX_PREFIX).lowercase()
        if (!HEX_DIGITS.matches(digits)) {
            refuse("calldata")
        }
        return digits
    }

    private fun refuse(field: String): Nothing {
        throw PerpsDepositException.QuoteRejected(PerpsQuoteError.UnexpectedOperation("$CHAIN_PAYMENT $field"))
    }

    private data class Call(val target: String, val value: BigInteger, val data: String) {

        val selector: String
            get() = data.take(SELECTOR_HEX_LENGTH)

        fun arguments(selector: String, words: Int): List<String>? = arguments(data, selector, words)

        companion object {

            fun arguments(data: String, selector: String, words: Int): List<String>? {
                if (data.length != SELECTOR_HEX_LENGTH + words * WORD_HEX_LENGTH || !data.startsWith(selector)) {
                    return null
                }
                return data.drop(SELECTOR_HEX_LENGTH).chunked(WORD_HEX_LENGTH)
            }
        }
    }
}
