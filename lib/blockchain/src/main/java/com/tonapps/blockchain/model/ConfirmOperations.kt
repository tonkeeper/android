package com.tonapps.blockchain.model

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import java.util.UUID

/**
 * Operations the wallet builds itself, for flows that have no backend envelope (send, dapp
 * requests, swap routes). What they carry is what gets signed; everything else stays on the
 * [ConfirmType] next to them.
 */
object ConfirmOperations {

    /**
     * A plain transfer of the sender's asset; [comment] rides where the chain has a memo. A Solana
     * payload names the SPL [token] it sends; elsewhere the token is the account's own.
     */
    fun transfer(
        chain: Chain,
        to: String,
        amount: BigInteger,
        comment: String? = null,
        fee: Fee? = null,
        token: Address? = null,
    ): Operation = single(chain, transferMeta(chain, Address.force(to, chain), amount, comment, token), fee)

    /** An EVM contract call. */
    fun call(
        chain: Chain,
        contract: String,
        data: String,
        amount: BigInteger,
        fee: Fee? = null,
    ): Operation {
        require(chain.isEvm) { "Contract calls are EVM-only, got ${chain.caip2}" }
        val meta = PayloadMeta.Evm.Main(to = Address.force(contract, chain), value = amount, data = data)
        return single(chain, meta, fee)
    }

    fun payload(index: Int, chain: Chain, meta: PayloadMeta, fee: Fee? = null): Payload = Payload(
        id = index.toString(),
        type = Payload.Type.Main,
        chain = chain,
        version = Payload.VERSION,
        fee = fee,
        meta = meta,
    )

    /** The plain-text comment of a single-payload operation, where its chain carries one. */
    fun comment(operation: Operation): String? = when (val meta = operation.payloads.singleOrNull()?.meta) {
        is PayloadMeta.Ton.Main -> meta.comment
        is PayloadMeta.Tron.Main -> meta.memo
        is PayloadMeta.Bitcoin.Main -> meta.memo
        else -> null
    }

    fun build(payloads: List<Payload>): Operation =
        Operation.build(UUID.randomUUID().toString(), payloads)

    private fun single(chain: Chain, meta: PayloadMeta, fee: Fee?): Operation =
        build(listOf(payload(0, chain, meta, fee)))

    private fun transferMeta(chain: Chain, to: Address, amount: BigInteger, comment: String?, token: Address?): PayloadMeta {
        return when {
            chain.isTvm -> PayloadMeta.Ton.Main(to = to, value = amount, comment = comment)
            // An EVM transfer carries no memo: calldata would turn it into a contract call.
            chain.isEvm -> PayloadMeta.Evm.Main(to = to, value = amount)
            chain is Chain.Tron -> PayloadMeta.Tron.Main(to = to, value = amount, memo = comment)
            chain is Chain.Bitcoin -> PayloadMeta.Bitcoin.Main(to = to, value = amount, memo = comment)
            // Binding holds the payload's mint to the account's asset: an SPL transfer without one reads as SOL.
            chain is Chain.Solana -> PayloadMeta.Solana.Main(to = to, value = amount, memo = comment, token = token)
            else -> throw IllegalArgumentException("Transfers on ${chain.caip2} have no operation payload")
        }
    }
}
