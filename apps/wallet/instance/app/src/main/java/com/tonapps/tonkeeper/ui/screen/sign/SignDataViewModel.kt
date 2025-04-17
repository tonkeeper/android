package com.tonapps.tonkeeper.ui.screen.sign

import android.app.Application
import android.net.Uri
import com.tonapps.base64.base64Encoded
import com.tonapps.base64.decodeBase64
import com.tonapps.base64.encodeBase64
import com.tonapps.blockchain.ton.TONOpCode
import com.tonapps.blockchain.ton.connect.TCAddress
import com.tonapps.blockchain.ton.connect.TCDomain
import com.tonapps.blockchain.ton.connect.TONProof
import com.tonapps.blockchain.ton.extensions.storeAddress
import com.tonapps.blockchain.ton.extensions.storeOpCode
import com.tonapps.blockchain.ton.extensions.storeStringTail
import com.tonapps.extensions.toByteArray
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.SignDataRequestPayload
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.usecase.sign.SignUseCase
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import io.github.andreypfau.kotlinx.crypto.crc32.crc32
import io.github.andreypfau.kotlinx.crypto.crc32.crc32c
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.crypto.digest.sha256
import org.ton.crypto.hex
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder

class SignDataViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val appUrl: Uri,
    private val signUseCase: SignUseCase,
): BaseWalletVM(app) {

    private companion object {
        private val prefix = "ton-connect/sign-data/".toByteArray()
    }

    private val domain: TCDomain
        get() = TCDomain(appUrl.host!!)

    private val address: TCAddress
        get() = TCAddress(wallet.contract.address)


    private fun getTimestamp(): Long {
        return System.currentTimeMillis() / 1000L
    }

    suspend fun signProof(content: String, type: String): TONProof.Result {
        val timestamp = getTimestamp()
        val signature = signString(timestamp, type, content)
        return TONProof.Result(
            timestamp = timestamp,
            domain = domain,
            payload = content,
            signature = signature.encodeBase64()
        )
    }

    suspend fun signCell(payload: SignDataRequestPayload.Cell): TONProof.Result {
        val timestamp = getTimestamp()
        val cell = buildCell(payload, timestamp)
        val signature = sign(cell.hash().toByteArray())
        return TONProof.Result(
            timestamp = timestamp,
            domain = domain,
            payload = payload.cellBase64,
            signature = signature.encodeBase64()
        )
    }

    private fun buildCell(payload: SignDataRequestPayload.Cell, timestamp: Long): Cell {
        val cell = payload.value
        val schemaHash = crc32(payload.schema.toByteArray())

        val domainCell = CellBuilder.beginCell().apply {
            storeStringTail(domain.value)
        }.endCell()

        return CellBuilder.beginCell().apply {
            storeOpCode(TONOpCode.SIGN_DATA)
            storeUInt(schemaHash, 32)
            storeUInt(timestamp, 64)
            storeAddress(wallet.contract.address)
            storeRef(domainCell)
            storeRef(cell)
        }.endCell()
    }

    private suspend fun signString(
        timestamp: Long,
        type: String,
        payload: String
    ): ByteArray = withContext(Dispatchers.IO) {
        val isText = type == "text"
        val payloadPrefix = if (isText) "txt" else "bin"
        val payloadBuffer = if (isText) payload.toByteArray() else payload.decodeBase64()

        val builder = ByteArrayOutputStream()
        builder.write(hex("ffff"))
        builder.write(prefix)
        builder.write(address.toByteArray(ByteOrder.BIG_ENDIAN))
        builder.write(domain.toByteArray(ByteOrder.BIG_ENDIAN))
        builder.write(timestamp.toByteArray(ByteOrder.BIG_ENDIAN))
        builder.write(payloadPrefix.toByteArray())
        builder.write(payloadBuffer.size.toByteArray(ByteOrder.BIG_ENDIAN))
        builder.write(payloadBuffer)

        val bytes = builder.toByteArray()

        sign(sha256(bytes))
    }

    private suspend fun sign(bytes: ByteArray): ByteArray {
        return signUseCase(context, wallet, bytes)
    }

}
