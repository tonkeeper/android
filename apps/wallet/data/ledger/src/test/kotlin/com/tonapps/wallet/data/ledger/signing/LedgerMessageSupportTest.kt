package com.tonapps.wallet.data.ledger.signing

import com.tonapps.chainkit.core.chain.model.MessageType
import com.tonapps.chainkit.core.chain.model.account.Chain
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerMessageSupportTest {

    @Test
    fun solanaSignsTransactionsOnlyNotMessages() {
        assertEquals(setOf(MessageType.Transaction), signed(Chain.Solana.Mainnet))
    }

    @Test
    fun tronSignsTransactionsAndPersonalMessages() {
        assertEquals(setOf(MessageType.Transaction, MessageType.Default), signed(Chain.Tron.Mainnet))
    }

    @Test
    fun evmSignsPersonalAndTypedMessages() {
        val expected = setOf(
            MessageType.Legacy,
            MessageType.LegacyEip155,
            MessageType.Default,
            MessageType.TypedMessage,
            MessageType.TypedMessageEip155,
        )
        assertEquals(expected, signed(Chain.Smartchain.Mainnet))
        assertEquals(expected, signed(Chain.Ethereum.Mainnet))
    }

    @Test
    fun tonMessagesAreNotSignedOnThisPath() {
        assertEquals(emptySet<MessageType>(), signed(Chain.Ton.Mainnet))
    }

    private fun signed(chain: Chain) = MessageType.entries.filter { chain.ledgerSignsMessage(it) }.toSet()
}
