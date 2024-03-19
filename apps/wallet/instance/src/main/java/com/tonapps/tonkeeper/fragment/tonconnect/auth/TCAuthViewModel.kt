package com.tonapps.tonkeeper.fragment.tonconnect.auth

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.tonkeeper.core.tonconnect.AppRepository
import com.tonapps.tonkeeper.core.tonconnect.Bridge
import com.tonapps.tonkeeper.core.tonconnect.Proof
import com.tonapps.tonkeeper.core.tonconnect.TCManifestRepository
import com.tonapps.tonkeeper.core.tonconnect.models.TCData
import com.tonapps.tonkeeper.core.tonconnect.models.TCItem
import com.tonapps.tonkeeper.core.tonconnect.models.TCRequest
import com.tonapps.tonkeeper.core.tonconnect.models.reply.TCAddressItemReply
import com.tonapps.tonkeeper.core.tonconnect.models.reply.TCConnectEventSuccess
import com.tonapps.tonkeeper.core.tonconnect.models.reply.TCReply
import com.tonapps.tonkeeper.password.PasscodeRepository
import com.tonapps.wallet.data.account.WalletRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import io.ktor.util.hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd
import org.ton.block.StateInit
import org.ton.boc.BagOfCells
import org.ton.cell.CellBuilder
import org.ton.crypto.base64
import org.ton.tlb.storeTlb

class TCAuthViewModel(
    private val request: TCRequest,
    private val walletRepository: WalletRepository,
    private val passcodeRepository: PasscodeRepository
): ViewModel() {

    private val _dataState = MutableStateFlow<TCData?>(null)
    val dataState = _dataState.asStateFlow().filterNotNull()

    private val bridge = Bridge()
    private val manifestRepository = TCManifestRepository()
    private val appRepository = AppRepository()
    private val proof = Proof()

    init {
        walletRepository.activeWalletFlow.onEach {
            requestData(it)
        }.launchIn(viewModelScope)
    }

    private fun requestData(wallet: WalletEntity) {
        viewModelScope.launch {
            val manifest = manifestRepository.manifest(request.payload.manifestUrl)

            val data = TCData(
                manifest = manifest,
                accountId = wallet.accountId,
                clientId = request.id,
                items = request.payload.items,
                testnet = wallet.testnet,
            )

            _dataState.tryEmit(data)
        }
    }

    private fun passcode(context: Context) = passcodeRepository.confirmationFlow(context)

    private fun wallet(context: Context) = passcode(context).combine(walletRepository.activeWalletFlow) { _, wallet ->
        wallet
    }.take(1)

    fun connect(context: Context): Flow<Unit> = wallet(context).combine(dataState) { wallet, data ->
        sendConnect(data, wallet)
    }.take(1).flowOn(Dispatchers.IO)

    private suspend fun sendConnect(data: TCData, wallet: WalletEntity) = withContext(Dispatchers.IO) {
        val accountId = wallet.accountId
        val app = appRepository.createApp(accountId, data.url, data.clientId)
        val privateKey = walletRepository.getPrivateKey(wallet.id)

        val items = mutableListOf<TCReply>()
        for (requestItem in data.items) {
            if (requestItem.name == TCItem.TON_ADDR) {
                items.add(createAddressItemReply(
                    accountId = accountId,
                    testnet = wallet.testnet,
                    stateInit = wallet.contract.stateInit,
                    publicKey = wallet.publicKey
                ))
            } else if (requestItem.name == TCItem.TON_PROOF) {
                items.add(proof.createProofItemReplySuccess(
                    requestItem.payload,
                    app.domain,
                    AddrStd.parse(accountId),
                    privateKey
                ))
            }
        }

        val event = TCConnectEventSuccess(items)
        bridge.sendEvent(event.toJSON(), app)
    }

    private fun createAddressItemReply(
        accountId: String,
        testnet: Boolean,
        stateInit: StateInit,
        publicKey: PublicKeyEd25519,
    ): TCAddressItemReply {
        val walletStateInit = getWalletStateInit(stateInit)

        val network = if (testnet) {
            TonNetwork.TESTNET
        } else {
            TonNetwork.MAINNET
        }

        return TCAddressItemReply(
            address = accountId,
            network = network.value.toString(),
            walletStateInit = walletStateInit,
            publicKey = hex(publicKey.toByteArray())
        )
    }

    private fun getWalletStateInit(
        stateInit: StateInit
    ): String {
        val cell = CellBuilder()
            .storeTlb(StateInit.tlbCodec(), stateInit)
            .endCell()
        return base64(BagOfCells(cell).toByteArray())
    }
}