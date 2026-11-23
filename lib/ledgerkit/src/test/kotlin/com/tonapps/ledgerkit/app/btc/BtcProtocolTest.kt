package com.tonapps.ledgerkit.app.btc

import com.tonapps.ledgerkit.apdu.Bip32Path
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BtcProtocolTest {

    private val elements = (1..7).map { size -> ByteArray(size) { size.toByte() } }

    @Test
    fun `merkle roots match the ledger reference implementation`() {
        val expected = listOf(
            "b413f47d13ee2fe6c845b2ee141af81de858df4ec549a58b7970bb96645bc8d2",
            "84fc9d22ba3729c733e692ea298171290f8bb06b942888d79638599cb719c7e3",
            "50ab465bb10ae8e2c17800cd6533a3311c0e2afd837585ff73f8c83dbf944f0b",
            "50e5a68865c866510164abc1fc4426ac7551b62e1ecdc39e296d8758d132e9bd",
            "ca54caf4d4545fd8ee07d8b55d729b05fd1a0fd89a20178c119bd0297b6dc2d3",
            "de8800092d1b2bed3aec153f4dd4fdf017fb0632471582921b62e7e83a6778fb",
            "36f2e0e463744b7b60a4c8d90823b85ced6ed1056b153a32b8c68839e3e435b6",
        )
        expected.forEachIndexed { index, root ->
            assertEquals(root, hex(BtcMerkleTree.ofElements(elements.take(index + 1)).root))
        }
        assertEquals("0000000000000000000000000000000000000000000000000000000000000000", hex(BtcMerkleTree(emptyList()).root))
    }

    @Test
    fun `merkle proof lists siblings from the leaf upwards`() {
        assertEquals(
            listOf(
                "2851ddf061ccde8675d83f08d671c6890d60a3176214a75b1778d7ce4fb3942f",
                "5a359a4dedf8493727ab85565ce2a92e4c67ab04ecba5b122815e1c5c3c7389c",
                "50e5a68865c866510164abc1fc4426ac7551b62e1ecdc39e296d8758d132e9bd",
            ),
            BtcMerkleTree.ofElements(elements).proof(5).map(::hex),
        )
    }

    @Test
    fun `wallet policy serialization and id match the reference`() {
        val policy = BtcWalletPolicy(
            name = "",
            descriptorTemplate = "wpkh(@0/**)",
            keysInfo = listOf(
                "[f5acc2fd/84'/1'/0']tpubDCtKfsNyRhULjZ9XMS4VKKtVcPdVDi8MKUbcSD9MJDyjRu1A2ND5MiipozyyspBT9bg8upEp7a8EAgFxNxXn1d7QkdbL52Ty5jiSLcxPt1P",
            ),
        )
        assertEquals(
            "02000bc8974a0d8bdd29024b2ddb7a7fe8df1d9801b270f4e6c1e7e1011ae39e7c9b000144006386daf887e322e999231f37ba1dcc1863357a4bc21a78a623d7d2863f02",
            hex(policy.serialize()),
        )
        assertEquals("74e9dad05d709eb46f8cce7378ebafb45a75c7e83e0d2a63e02f492c8adc7293", hex(policy.id))
    }

    @Test
    fun `merkleized map commitment sorts keys`() {
        val map = BtcMerkleizedMap(
            mapOf(
                ByteArrayKey(byteArrayOf(0x02)) to littleEndian(2, 4),
                ByteArrayKey(byteArrayOf(0x04)) to byteArrayOf(0x01),
                ByteArrayKey(byteArrayOf(0x05)) to byteArrayOf(0x02),
                ByteArrayKey(byteArrayOf(0x03)) to littleEndian(0, 4),
                ByteArrayKey(byteArrayOf(0xfb.toByte())) to littleEndian(2, 4),
            ),
        )
        assertEquals(
            "05519b38dae74447b72151f354cb138ca3591a5ff8ac813289b18a004e3132162086d8d9498a323006ec5982eeb4ea7c41d27020d57985512ab59ff8f40d501507",
            hex(map.commitment()),
        )
    }

    @Test
    fun `get preimage streams the tail through get more elements`() {
        val interpreter = BtcClientCommandInterpreter()
        val preimage = ByteArray(256) { it.toByte() } + ByteArray(44) { it.toByte() }
        interpreter.addKnownPreimage(preimage)
        val first = interpreter.execute(byteArrayOf(0x40, 0x00) + sha256(preimage))
        assertEquals("fd2c01fb", hex(first.copyOfRange(0, 4)))
        assertArrayEquals(preimage.copyOfRange(0, 251), first.copyOfRange(4, first.size))
        assertEquals(
            "3101fbfcfdfeff000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b",
            hex(interpreter.execute(byteArrayOf(0xa0.toByte()))),
        )
    }

    @Test
    fun `merkle leaf proof and index commands match the reference`() {
        val interpreter = BtcClientCommandInterpreter()
        val list = (1..200).map { index -> ByteArray(1 + index % 5) { (index % 256).toByte() } }
        interpreter.addKnownList(list)
        val tree = BtcMerkleTree.ofElements(list)
        assertEquals("263642970093e0d94cc709f79c849e243c73e129820f46c7a8657b9a4d495c0b", hex(tree.root))
        val proof = interpreter.execute(byteArrayOf(0x41) + tree.root + CompactSize.encode(200) + CompactSize.encode(0x57))
        assertEquals(
            "f8c18151ea133c2fc453adb926f3a8d78563337497aac6699b1ff02d3aee5a1108069215ababade5896ffb52c95a39ee115d9b2dff2725aa5f155938980fd01fc87534c0441015cbc7bfd7a3a384aa85c7e1d78dbc2e443a23aceae82d7be29ab0b0a33a37397a152fc3cdef775cde61997467c9e3878aae2c1c43e66f46a32dcd07838de88c90387b398ed72e92da5073db7652e9e3b6789dab64dd6616e9195da3d32cd083b8d61d1ec794659a0e91a4ef80313ea31c10716d69405cf5f50231b6b5826667e750abf8cde2c4bb1b1f8437230dbc8f23b0bd91d94b8e06155f5cb4",
            hex(proof),
        )
        assertEquals(
            "02201c9736a1a234a770563f82bd0a7979015d23055fda90d0fd50b0c2ba8b485bcbf380924de96bd19abad7744cb2bb8a2ca5dded080e2853a72eed4c8a077d773c",
            hex(interpreter.execute(byteArrayOf(0xa0.toByte()))),
        )
        assertEquals("0107", hex(interpreter.execute(byteArrayOf(0x42) + tree.root + BtcMerkleTree.elementHash(list[7]))))
        assertEquals("0000", hex(interpreter.execute(byteArrayOf(0x42) + tree.root + ByteArray(32))))
    }

    @Test
    fun `yield collects device values`() {
        val interpreter = BtcClientCommandInterpreter()
        assertEquals(0, interpreter.execute(byteArrayOf(0x10, 0x01, 0x02)).size)
        assertArrayEquals(byteArrayOf(0x01, 0x02), interpreter.yielded.single())
    }

    @Test
    fun `bech32 p2wpkh address from compressed public key`() {
        val publicKey = unhex("0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798")
        assertEquals("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4", BtcScripts.p2wpkhAddress(publicKey))
        assertEquals("0014751e76e8199196d454941c45d1b3a323f1433bd6", hex(BtcScripts.p2wpkhScript(publicKey)))
        assertEquals(
            "0014751e76e8199196d454941c45d1b3a323f1433bd6",
            hex(BtcScripts.scriptForSegwitAddress("BC1QW508D6QEJXTDG4Y5R3ZARVARY0C5XW7KV8F3T4")),
        )
    }

    @Test
    fun `xpub parses and converts to zpub`() {
        val xpub = BtcExtendedPublicKey.parse(
            "xpub661MyMwAqRbcFtXgS5sYJABqqG9YLmC4Q1Rdap9gSE8NqtwybGhePY2gZ29ESFjqJoCu1Rupje8YtGqsefD265TMg7usUDFdp6W1EGMcet8",
        )
        assertEquals("0339a36013301597daef41fbe593a02cc513d0b55527ec2df1050e2e8ff49c85c2", hex(xpub.publicKey))
        assertEquals(
            "zpub6jftahH18ngZxUuv6oSniLNrBCSSE1B4EEU59bwTCEt8x6aS6b2mdfLxbS4QS53g85SWWP6wexqeer516433gYpZQoJie2tcMYdJ1SYYYAL",
            xpub.toZpub(),
        )
    }

    @Test
    fun `raw transaction round trip keeps txid on witness serialization`() {
        val transaction = BtcRawTransaction(
            version = 2,
            inputs = listOf(BtcRawTransaction.Input(ByteArray(32) { 1 }, 3, ByteArray(0), 0xffffffffL)),
            outputs = listOf(BtcRawTransaction.Output(12_345, unhex("0014751e76e8199196d454941c45d1b3a323f1433bd6"))),
            witnesses = listOf(listOf(ByteArray(71) { 2 }, ByteArray(33) { 3 })),
            lockTime = 0,
        )
        val parsed = BtcRawTransaction.parse(transaction.serialize())
        assertArrayEquals(transaction.serialize(), parsed.serialize())
        assertArrayEquals(transaction.txidInternal, parsed.txidInternal)
        assertEquals(12_345, parsed.outputs.single().amount)
        assertArrayEquals(transaction.serialize(includeWitness = false), BtcRawTransaction.parse(transaction.serialize(false)).serialize())
    }

    @Test
    fun `psbt contains the mandatory maps and a fee that balances`() {
        val publicKey = unhex("0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798")
        val unsigned = BtcUnsignedTransaction(
            inputs = listOf(
                BtcSpendInput(
                    previousTxidInternal = ByteArray(32) { 9 },
                    previousIndex = 1,
                    amount = 100_000,
                    scriptPubKey = BtcScripts.p2wpkhScript(publicKey),
                    publicKey = publicKey,
                    path = Bip32Path.parse("m/84'/0'/0'/0/0"),
                    previousTransaction = null,
                ),
            ),
            outputs = listOf(BtcSpendOutput(60_000, BtcScripts.p2wpkhScript(publicKey)), BtcSpendOutput(39_000, BtcScripts.p2wpkhScript(publicKey))),
        )
        assertEquals(1_000, unsigned.fee)
        val psbt = BtcPsbtV2.build(unsigned, byteArrayOf(1, 2, 3, 4))
        assertEquals(listOf("02", "03", "04", "05", "fb"), psbt.global.sortedKeys.map(::hex))
        assertEquals(listOf("01", "06" + hex(publicKey), "0e", "0f", "10"), psbt.inputs.single().sortedKeys.map(::hex))
        assertEquals("0102030454000080000000800000008000000000" + "00000000", hex(psbt.inputs.single().sortedValues[1]))
        val signed = unsigned.finalize(mapOf(0 to ByteArray(72)))
        assertEquals(2, signed.witnesses.single().size)
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun unhex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
