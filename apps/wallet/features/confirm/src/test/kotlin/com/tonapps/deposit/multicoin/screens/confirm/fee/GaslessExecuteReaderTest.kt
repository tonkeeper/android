package com.tonapps.deposit.multicoin.screens.confirm.fee

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestWallet
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GaslessExecuteReaderTest : BaseUnitTest() {

    private val requested = listOf(call(USDT, APPROVE), call(DEPOSITORY, DEPOSIT))

    @Test
    fun theBatteryCarriesExactlyTheCallsAsked() {
        GaslessExecuteReader.read(operation(listOf(USDT to APPROVE, DEPOSITORY to DEPOSIT)), requested, Asset.Battery.ID, BATTERY_FEE)
    }

    @Test
    fun aTokenFeeIsOneTransferOfThatTokenAheadOfTheCallsAsked() {
        GaslessExecuteReader.read(
            operation(listOf(USDT to FEE_TRANSFER, USDT to APPROVE, DEPOSITORY to DEPOSIT), mode = TOKEN_MODE),
            requested,
            USDT_ID,
            TOKEN_FEE,
        )
    }

    @Test
    fun aTokenFeeAheadOfOneCallRunsInTheModeTheRelayerSendsForTwoCalls() {
        GaslessExecuteReader.read(
            operation(listOf(USDT to FEE_TRANSFER, DEPOSITORY to DEPOSIT), mode = TWO_CALL_TOKEN_MODE),
            listOf(call(DEPOSITORY, DEPOSIT)),
            USDT_ID,
            TOKEN_FEE,
        )
    }

    @Test
    fun aTokenFeeBatchTheRelayerRunsAllOrNothingIsSigned() {
        GaslessExecuteReader.read(
            operation(listOf(USDT to FEE_TRANSFER, USDT to APPROVE, DEPOSITORY to DEPOSIT), mode = ALL_OR_NOTHING_TOKEN_MODE),
            requested,
            USDT_ID,
            TOKEN_FEE,
        )
    }

    @Test
    fun aTokenFeeAheadOfOneCallTheRelayerRunsAllOrNothingIsSigned() {
        GaslessExecuteReader.read(
            operation(listOf(USDT to FEE_TRANSFER, DEPOSITORY to DEPOSIT), mode = TWO_CALL_TOKEN_MODE.dropLast(2) + "05"),
            listOf(call(DEPOSITORY, DEPOSIT)),
            USDT_ID,
            TOKEN_FEE,
        )
    }

    @Test
    fun aDelegationToHermesOnTheSameChainIsSigned() {
        GaslessExecuteReader.read(
            operation(listOf(USDT to APPROVE, DEPOSITORY to DEPOSIT), authorization = HERMES to BSC_CHAIN_ID),
            requested,
            Asset.Battery.ID,
            BATTERY_FEE,
        )
    }

    @Test
    fun anythingElseIsRefused() {
        val cases: Map<String, () -> Unit> = mapOf(
            "a call missing" to { read(operation(listOf(DEPOSITORY to DEPOSIT))) },
            "a call added" to { read(operation(listOf(USDT to APPROVE, DEPOSITORY to DEPOSIT, USDT to transfer(RELAYER, BigInteger.ONE)))) },
            "a call swapped for a transfer" to { read(operation(listOf(USDT to APPROVE, USDT to transfer(RELAYER, AMOUNT)))) },
            "a call to another contract" to { read(operation(listOf(USDT to APPROVE, RELAYER to DEPOSIT))) },
            "calls in another order" to { read(operation(listOf(DEPOSITORY to DEPOSIT, USDT to APPROVE))) },
            "a fee transfer paid in charges" to { read(operation(listOf(USDT to FEE_TRANSFER, USDT to APPROVE, DEPOSITORY to DEPOSIT))) },
            "a fee transfer over the fee" to {
                read(operation(listOf(USDT to transfer(RELAYER, TOKEN_FEE + BigInteger.ONE), USDT to APPROVE, DEPOSITORY to DEPOSIT), mode = TOKEN_MODE), USDT_ID, TOKEN_FEE)
            },
            "a fee transfer of another token" to {
                read(operation(listOf(OTHER_TOKEN to FEE_TRANSFER, USDT to APPROVE, DEPOSITORY to DEPOSIT), mode = TOKEN_MODE), USDT_ID, TOKEN_FEE)
            },
            "a fee approval" to {
                read(operation(listOf(USDT to APPROVE, USDT to APPROVE, DEPOSITORY to DEPOSIT), mode = TOKEN_MODE), USDT_ID, TOKEN_FEE)
            },
            "a fee transfer after the calls" to {
                read(operation(listOf(USDT to APPROVE, DEPOSITORY to DEPOSIT, USDT to FEE_TRANSFER), mode = TOKEN_MODE), USDT_ID, TOKEN_FEE)
            },
            "two fee transfers" to {
                read(operation(listOf(USDT to FEE_TRANSFER, USDT to FEE_TRANSFER, USDT to APPROVE, DEPOSITORY to DEPOSIT), mode = TOKEN_MODE), USDT_ID, TOKEN_FEE)
            },
            "a delegation to another contract" to { read(operation(listOf(USDT to APPROVE, DEPOSITORY to DEPOSIT), authorization = RELAYER to BSC_CHAIN_ID)) },
            "a delegation on another chain" to { read(operation(listOf(USDT to APPROVE, DEPOSITORY to DEPOSIT), authorization = HERMES to 1L)) },
            "an Execute for another chain" to { read(operation(listOf(USDT to APPROVE, DEPOSITORY to DEPOSIT), domainChainId = 1L)) },
            "an Execute for another account" to { read(operation(listOf(USDT to APPROVE, DEPOSITORY to DEPOSIT), owner = RELAYER)) },
            "an Execute for another delegate" to { read(operation(listOf(USDT to APPROVE, DEPOSITORY to DEPOSIT), salt = RELAYER)) },
            "an Execute the account runs itself" to { read(operation(listOf(USDT to APPROVE, DEPOSITORY to DEPOSIT), mode = SELF_MODE)) },
            "a plain batch with policies" to { read(operation(listOf(USDT to APPROVE, DEPOSITORY to DEPOSIT), mode = BATTERY_MODE.dropLast(2) + "29")) },
            "the calls asked in try mode" to { read(operation(listOf(USDT to APPROVE, DEPOSITORY to DEPOSIT), mode = TOKEN_MODE)) },
            "a token fee in the plain mode" to {
                read(operation(listOf(USDT to FEE_TRANSFER, USDT to APPROVE, DEPOSITORY to DEPOSIT)), USDT_ID, TOKEN_FEE)
            },
            "a token fee with policies for another number of calls" to {
                read(operation(listOf(USDT to FEE_TRANSFER, USDT to APPROVE, DEPOSITORY to DEPOSIT), mode = TWO_CALL_TOKEN_MODE), USDT_ID, TOKEN_FEE)
            },
            "a token fee with other policies" to {
                read(operation(listOf(USDT to FEE_TRANSFER, USDT to APPROVE, DEPOSITORY to DEPOSIT), mode = TOKEN_MODE.dropLast(2) + "19"), USDT_ID, TOKEN_FEE)
            },
            "a token fee transfer allowed to fail" to {
                read(operation(listOf(USDT to FEE_TRANSFER, USDT to APPROVE, DEPOSITORY to DEPOSIT), mode = TOKEN_MODE.dropLast(2) + "2a"), USDT_ID, TOKEN_FEE)
            },
            "an all-or-nothing token fee for another number of calls" to {
                read(operation(listOf(USDT to FEE_TRANSFER, USDT to APPROVE, DEPOSITORY to DEPOSIT), mode = TOKEN_MODE.dropLast(2) + "05"), USDT_ID, TOKEN_FEE)
            },
            "two Executes" to {
                val execute = operation(listOf(USDT to APPROVE, DEPOSITORY to DEPOSIT)).payloads
                read(Operation.build("operation", execute + execute))
            },
        )

        val signed = cases.mapNotNull { (case, read) ->
            val refused = try {
                read()
                false
            } catch (e: IllegalStateException) {
                true
            }
            case.takeUnless { refused }
        }

        assertEquals(emptyList<String>(), signed)
    }

    @Test
    fun anOperationForAnotherSenderIsRefused() {
        val calls = requested.map { GaslessRelayer.Call(it.type, it.chainId, RELAYER, it.to, it.value, it.data) }

        assertThrows(IllegalStateException::class.java) {
            GaslessExecuteReader.read(operation(listOf(USDT to APPROVE, DEPOSITORY to DEPOSIT)), calls, Asset.Battery.ID, BATTERY_FEE)
        }
    }

    private fun read(operation: Operation, assetId: String = Asset.Battery.ID, fee: BigInteger = BATTERY_FEE) {
        GaslessExecuteReader.read(operation, requested, assetId, fee)
    }

    private fun call(to: String, data: String) = GaslessRelayer.Call(
        type = GaslessRelayer.Call.Type.Swap,
        chainId = "bsc/mainnet",
        sender = TestWallet.ETH_ADDRESS,
        to = to,
        value = BigInteger.ZERO,
        data = data,
    )

    private fun operation(
        calls: List<Pair<String, String>>,
        mode: String = BATTERY_MODE,
        authorization: Pair<String, Long>? = null,
        domainChainId: Long = BSC_CHAIN_ID,
        owner: String = TestWallet.ETH_ADDRESS,
        salt: String = HERMES,
    ): Operation {
        val envelope = buildJsonObject {
            put("id", "81b0641a-b080-4a93-8c5c-b2bae5c43380")
            putJsonArray("payloads") {
                authorization?.let { (address, chainId) ->
                    addJsonObject {
                        put("id", "p_auth")
                        put("type", "eip7702/authorization")
                        put("chain_id", "bsc/mainnet")
                        put("version", 1)
                        putJsonObject("meta") {
                            put("chainId", chainId)
                            put("address", address)
                            put("nonce", NONCE)
                        }
                    }
                }
                addJsonObject {
                    put("id", "c459ed6b-e295-481c-9c47-a5699b36a465")
                    put("type", "eip7821/hermes/delegate")
                    put("chain_id", "bsc/mainnet")
                    put("version", 1)
                    putJsonObject("meta") {
                        put("data", document(calls, mode, domainChainId, owner, salt))
                    }
                }
            }
        }
        return Operation.parse("81b0641a-b080-4a93-8c5c-b2bae5c43380", envelope.toString()).getOrNull()!!
    }

    private fun document(calls: List<Pair<String, String>>, mode: String, chainId: Long, owner: String, salt: String): JsonObject = buildJsonObject {
        putJsonObject("domain") {
            put("chainId", chainId)
            put("name", "Hermes")
            put("salt", "0x" + salt.removePrefix("0x").lowercase().padStart(64, '0'))
            put("verifyingContract", owner)
            put("version", "v1.0.0")
        }
        putJsonObject("message") {
            put(
                "calls",
                JsonArray(
                    calls.map { (target, data) ->
                        buildJsonObject {
                            put("data", data)
                            put("target", target)
                            put("value", "0")
                        }
                    },
                ),
            )
            put("deadline", "1794412631")
            put("mode", mode)
            put("nonce", "11")
        }
        put("primaryType", "Execute")
        putJsonObject("types") {
            put("Call", fields("target" to "address", "value" to "uint256", "data" to "bytes"))
            put(
                "EIP712Domain",
                fields("name" to "string", "version" to "string", "chainId" to "uint256", "verifyingContract" to "address", "salt" to "bytes32"),
            )
            put("Execute", fields("mode" to "bytes32", "calls" to "Call[]", "nonce" to "uint256", "deadline" to "uint256"))
        }
    }

    private fun fields(vararg fields: Pair<String, String>): JsonArray = buildJsonArray {
        fields.forEach { (name, type) ->
            addJsonObject {
                put("name", name)
                put("type", type)
            }
        }
    }

    private fun transfer(to: String, amount: BigInteger): String {
        return "0xa9059cbb" + to.lowercase().removePrefix("0x").padStart(64, '0') + amount.toString(16).padStart(64, '0')
    }

    private companion object {
        const val BSC_CHAIN_ID = 56L
        const val NONCE = 12
        const val HERMES = "0x24b576BC271823bF9C24BE627B4363b9b00191e9"
        const val USDT = "0x55d398326f99059fF775485246999027B3197955"
        const val USDT_ID = "bsc/mainnet/erc20/0x55d398326f99059ff775485246999027b3197955"
        const val OTHER_TOKEN = "0x8ac76a51cc950d9822d68b83fe1ad97b32cd580d"
        const val DEPOSITORY = "0x4cD00E387622C35bDDB9b4c962C136462338BC31"
        const val RELAYER = "0x45a9034c5dc4e1731b7ff8f977e50012822d5b9d"
        const val BATTERY_MODE = "0x0100000000007821000100000000000000000000000000000000000000000000"
        const val TOKEN_MODE = "0x0101000000007821000100000000000000000000000000000000000000000029"
        const val TWO_CALL_TOKEN_MODE = "0x0101000000007821000100000000000000000000000000000000000000000009"
        const val ALL_OR_NOTHING_TOKEN_MODE = "0x0101000000007821000100000000000000000000000000000000000000000015"
        const val SELF_MODE = "0x0100000000000000000000000000000000000000000000000000000000000000"
        const val APPROVE =
            "0x095ea7b30000000000000000000000004cd00e387622c35bddb9b4c962c136462338bc3100000000000000000000000000000000000000000000000029a2241af62c0000"
        const val DEPOSIT =
            "0xe80179520000000000000000000000000ee3cdee4f010bf6c49af86e9e67ef2bc80a508b00000000000000000000000055d398326f99059ff775485246999027b3197955" +
                "00000000000000000000000000000000000000000000000029a2241af62c00002deedc143eb2127c719ea6ffeeb96c418eb1e4876939c4a03489b1b758fe3970"
        const val FEE_TRANSFER =
            "0xa9059cbb00000000000000000000000045a9034c5dc4e1731b7ff8f977e50012822d5b9d000000000000000000000000000000000000000000000000001472ec88825047"
        val AMOUNT: BigInteger = BigInteger.parseString("3000000000000000000")
        val BATTERY_FEE: BigInteger = BigInteger.fromInt(2)
        val TOKEN_FEE: BigInteger = BigInteger.parseString("5755859762303047")
    }
}
