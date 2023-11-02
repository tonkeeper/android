package ton

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.mnemonic.Mnemonic

class WalletManager(
    application: Application
) {

    companion object {
        const val MNEMONIC_WORD_COUNT = Mnemonic.DEFAULT_WORD_COUNT
    }

    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tonWrapper = TonWrapper(scope)
    private val storage = SafeStorage(application)
    private var walletInfo: WalletInfo? = null

    init {
        scope.launch {
            walletInfo = storage.getWallet()
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        storage.clear()
        walletInfo = null
    }

    suspend fun createWallet() = withContext(Dispatchers.IO) {
        val walletInfo = tonWrapper.createWallet()
        storage.saveWallet(walletInfo)
    }

    suspend fun getWalletInfo() = withContext(Dispatchers.IO) {
        walletInfo ?: storage.getWallet()
    }

    suspend fun restoreWallet(words: List<String>) = withContext(Dispatchers.IO) {
        try {
            val walletInfo = tonWrapper.restoreWallet(words)
            storage.saveWallet(walletInfo)
        } catch (e: Throwable) {}
    }


}
