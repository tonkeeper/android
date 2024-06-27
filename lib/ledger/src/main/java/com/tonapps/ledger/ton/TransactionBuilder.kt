package com.tonapps.ledger.ton

import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.StateInit

class TransactionBuilder {
    private lateinit var destination: AddrStd
    private var sendMode: Int = 0
    private var seqno: Int = 0
    private var timeout: Int = 0
    private var bounceable: Boolean = false
    private lateinit var coins: Coins
    private var stateInit: StateInit? = null
    private var payload: TonPayloadFormat? = null

    fun setDestination(to: AddrStd) = apply { this.destination = to }
    fun setSendMode(sendMode: Int) = apply { this.sendMode = sendMode }
    fun setSeqno(seqno: Int) = apply { this.seqno = seqno }
    fun setTimeout(timeout: Int) = apply { this.timeout = timeout }
    fun setBounceable(bounce: Boolean) = apply { this.bounceable = bounce }
    fun setCoins(amount: Coins) = apply { this.coins = amount }
    fun setStateInit(stateInit: StateInit?) = apply { this.stateInit = stateInit }
    fun setPayload(payload: TonPayloadFormat?) = apply { this.payload = payload }

    fun build(): Transaction {
        if (!::destination.isInitialized || !::coins.isInitialized) {
            throw IllegalArgumentException("To and Amount must be initialized")
        }
        return Transaction(
            destination = destination,
            sendMode = sendMode,
            seqno = seqno,
            timeout = timeout,
            bounceable = bounceable,
            coins = coins,
            stateInit = stateInit,
            payload = payload
        )
    }
}