package com.tonapps.blockchain.tron

import com.google.protobuf.ByteString
import com.tonapps.blockchain.tron.proto.Chain.Transaction
import com.tonapps.blockchain.tron.proto.Common
import com.tonapps.blockchain.tron.proto.Contract
import org.bitcoinj.core.Base58
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger
import com.google.protobuf.Any as ProtoAny

class TronVerificationTest {

    private val owner = tronAddress(0x11)
    private val recipient = tronAddress(0x22)
    private val usdtContract = tronAddress(0x33)
    private val stranger = tronAddress(0x44)

    private val transfer = TronTransfer(
        from = owner,
        to = recipient,
        amount = BigInteger.valueOf(1_500_000),
        contractAddress = usdtContract,
    )

    private val trc20Request = TronTransactionRequest.Trc20(
        transfer = transfer,
        feeLimit = FEE_LIMIT,
    )

    private val trxRequest = TronTransactionRequest.Trx(
        from = owner,
        to = recipient,
        amountSun = AMOUNT_SUN,
    )

    @Test
    fun honestNodeBuildsTheRequestedTrc20Transfer() {
        verify(trc20Request, triggerRaw())
    }

    @Test
    fun honestNodeBuildsTheRequestedTrxTransfer() {
        verify(trxRequest, transferRaw())
    }

    @Test
    fun feeLimitBelowTheRequestedOneIsAccepted() {
        verify(trc20Request, triggerRaw(feeLimit = FEE_LIMIT - 1))
    }

    @Test
    fun comparisonIgnoresHexCase() {
        val contract = TronContract.SmartContractCall(
            ownerAddress = owner.tronHex().uppercase(),
            feeLimit = FEE_LIMIT,
            contractAddress = usdtContract.tronHex().uppercase(),
            callData = transfer.callData.uppercase(),
            callValue = 0,
            callTokenValue = 0,
            tokenId = 0,
        )

        trc20Request.requireMatches(contract)
    }

    @Test
    fun substitutedSenderIsRejected() {
        assertMismatch("owner_address", trc20Request, triggerRaw(owner = stranger))
    }

    @Test
    fun substitutedTokenContractIsRejected() {
        assertMismatch("contract_address", trc20Request, triggerRaw(contract = stranger))
    }

    @Test
    fun substitutedRecipientInsideCallDataIsRejected() {
        val hijacked = transfer.copy(to = stranger)

        assertMismatch("data", trc20Request, triggerRaw(data = hijacked.callData))
    }

    @Test
    fun trxAttachedToASmartContractCallIsRejected() {
        assertMismatch("call_value", trc20Request, triggerRaw(callValue = 1))
    }

    @Test
    fun trc10TokenAttachedToASmartContractCallIsRejected() {
        assertMismatch("call_token_value", trc20Request, triggerRaw(callTokenValue = 1))
        assertMismatch("token_id", trc20Request, triggerRaw(tokenId = 1))
    }

    @Test
    fun feeLimitAboveTheRequestedOneIsRejected() {
        assertMismatch("fee_limit", trc20Request, triggerRaw(feeLimit = FEE_LIMIT + 1))
    }

    @Test
    fun nativeTransferInsteadOfASmartContractCallIsRejected() {
        assertMismatch("contract_type", trc20Request, transferRaw(feeLimit = FEE_LIMIT))
    }

    @Test
    fun substitutedTrxRecipientIsRejected() {
        assertMismatch("to_address", trxRequest, transferRaw(to = stranger))
    }

    @Test
    fun substitutedTrxAmountIsRejected() {
        assertMismatch("amount", trxRequest, transferRaw(amount = AMOUNT_SUN + 1))
    }

    @Test
    fun substitutedTrxSenderIsRejected() {
        assertMismatch("owner_address", trxRequest, transferRaw(owner = stranger))
    }

    @Test
    fun feeLimitOnANativeTransferIsRejected() {
        assertMismatch("fee_limit", trxRequest, transferRaw(feeLimit = 1))
    }

    @Test
    fun smartContractCallInsteadOfANativeTransferIsRejected() {
        assertMismatch("contract_type", trxRequest, triggerRaw(feeLimit = 0))
    }

    @Test
    fun contractCarryingAProviderIsRejected() {
        assertMismatch("provider", trc20Request, triggerRaw { provider = ONE_BYTE })
    }

    @Test
    fun contractCarryingAContractNameIsRejected() {
        assertMismatch("contract_name", trc20Request, triggerRaw { contractName = ONE_BYTE })
    }

    @Test
    fun contractCarryingAPermissionIdIsRejected() {
        assertMismatch("permission_id", trc20Request, triggerRaw { permissionId = 2 })
    }

    @Test
    fun transactionCarryingAMemoIsRejected() {
        val raw = triggerRaw().toBuilder().setData(ONE_BYTE).build()

        assertMismatch("data", trc20Request, raw)
    }

    @Test
    fun transactionCarryingAuthsIsRejected() {
        val raw = triggerRaw().toBuilder()
            .addAuths(Common.authority.newBuilder().build())
            .build()

        assertMismatch("auths", trc20Request, raw)
    }

    @Test
    fun transactionCarryingScriptsIsRejected() {
        val raw = triggerRaw().toBuilder().setScripts(ONE_BYTE).build()

        assertMismatch("scripts", trc20Request, raw)
    }

    @Test
    fun transactionWithoutContractsIsRejected() {
        assertMismatch("contract_count", trc20Request, triggerRaw(contracts = 0))
    }

    @Test
    fun transactionCarryingASecondContractIsRejected() {
        assertMismatch("contract_count", trc20Request, triggerRaw(contracts = 2))
    }

    private fun verify(request: TronTransactionRequest, raw: Transaction.raw) {
        TronTransaction.fromRawDataHex(raw.toByteArray().toTronHex(), request)
    }

    private fun assertMismatch(
        field: String,
        request: TronTransactionRequest,
        raw: Transaction.raw,
    ) {
        val exception = assertThrows(TronTransactionMismatchException::class.java) {
            verify(request, raw)
        }

        assertEquals(field, exception.field)
    }

    private fun triggerRaw(
        owner: String = this.owner,
        contract: String = usdtContract,
        data: String = transfer.callData,
        callValue: Long = 0,
        callTokenValue: Long = 0,
        tokenId: Long = 0,
        feeLimit: Long = FEE_LIMIT,
        contracts: Int = 1,
        extra: Transaction.Contract.Builder.() -> Unit = {},
    ): Transaction.raw {
        val parameter = Contract.TriggerSmartContract.newBuilder()
            .setOwnerAddress(owner.tronBytes())
            .setContractAddress(contract.tronBytes())
            .setData(data.hexBytes())
            .setCallValue(callValue)
            .setCallTokenValue(callTokenValue)
            .setTokenId(tokenId)
            .build()

        return raw(
            type = Transaction.Contract.ContractType.TriggerSmartContract,
            parameter = parameter.toByteString(),
            feeLimit = feeLimit,
            contracts = contracts,
            extra = extra,
        )
    }

    private fun transferRaw(
        owner: String = this.owner,
        to: String = recipient,
        amount: Long = AMOUNT_SUN,
        feeLimit: Long = 0,
        contracts: Int = 1,
    ): Transaction.raw {
        val parameter = Contract.TransferContract.newBuilder()
            .setOwnerAddress(owner.tronBytes())
            .setToAddress(to.tronBytes())
            .setAmount(amount)
            .build()

        return raw(
            type = Transaction.Contract.ContractType.TransferContract,
            parameter = parameter.toByteString(),
            feeLimit = feeLimit,
            contracts = contracts,
        )
    }

    private fun raw(
        type: Transaction.Contract.ContractType,
        parameter: ByteString,
        feeLimit: Long,
        contracts: Int,
        extra: Transaction.Contract.Builder.() -> Unit = {},
    ): Transaction.raw {
        val contract = Transaction.Contract.newBuilder()
            .setType(type)
            .setParameter(ProtoAny.newBuilder().setValue(parameter).build())
            .apply(extra)
            .build()

        val builder = Transaction.raw.newBuilder()
            .setExpiration(EXPIRATION)
            .setTimestamp(TIMESTAMP)
            .setFeeLimit(feeLimit)

        repeat(contracts) { builder.addContract(contract) }

        return builder.build()
    }

    private fun String.tronBytes(): ByteString = ByteString.copyFrom(tronHex().hexToBytes())

    private fun String.hexBytes(): ByteString = ByteString.copyFrom(hexToBytes())

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {

        const val FEE_LIMIT = 150_000_000L
        const val AMOUNT_SUN = 2_000_000L
        const val EXPIRATION = 1_700_000_060_000L
        const val TIMESTAMP = 1_700_000_000_000L

        val ONE_BYTE: ByteString = ByteString.copyFrom(byteArrayOf(1))

        fun tronAddress(seed: Int): String = Base58.encodeChecked(0x41, ByteArray(20) { seed.toByte() })
    }
}
