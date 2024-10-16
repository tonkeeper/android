package com.tonapps.tonkeeper.ui.screen.nft

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.TonTransferHelper
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.icu.Coins
import com.tonapps.tonkeeper.core.entities.SendMetadataEntity
import com.tonapps.tonkeeper.core.entities.TransferEntity
import com.tonapps.tonkeeper.extensions.toGrams
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.send.main.helper.SendNftHelper
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionScreen
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.core.entity.RawMessageEntity
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.TokenPrefsEntity
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.localization.Localization
import io.tonapi.models.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.block.AddrStd
import java.math.BigInteger

class NftViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val nft: NftEntity,
    private val settingsRepository: SettingsRepository,
    private val api: API,
): BaseWalletVM(app) {

    val burnAddress: String by lazy {
        api.getBurnAddress()
    }

    fun reportSpam(spam: Boolean, callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            loading(true)
            val state = if (spam) TokenPrefsEntity.State.SPAM else TokenPrefsEntity.State.TRUST
            settingsRepository.setTokenState(wallet.id, nft.address, state)
            try {
                api.reportNtfSpam(nft.address, spam)
                withContext(Dispatchers.Main) {
                    callback()
                }
            } catch (e: Throwable) {
                toast(Localization.unknown_error)
            }
            loading(false)
        }
    }

    fun hideCollection(callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val address = nft.collection?.address ?: nft.address
            settingsRepository.setTokenState(wallet.id, address, TokenPrefsEntity.State.SPAM)
            withContext(Dispatchers.Main) {
                callback()
            }
        }
    }
}