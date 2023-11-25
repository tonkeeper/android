package ton

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.ton.api.liteclient.config.LiteClientConfigGlobal
import org.ton.block.AccountInfo
import org.ton.lite.client.LiteClient
import org.ton.mnemonic.Mnemonic
import ton.wallet.WalletInfo
import java.net.URL

internal class TonWrapper(
    scope: CoroutineScope
) {

    var liteClient: LiteClient? = null

    init {
        scope.launch {
            val config = getLiteClientConfig()
            liteClient = LiteClient(scope.coroutineContext, config)
        }
    }

    private suspend fun getLiteClientConfig(): LiteClientConfigGlobal = withContext(Dispatchers.IO) {
        val data = URL("https://ton.org/global-config.json").readText()
        val json = Json { ignoreUnknownKeys = true }
        json.decodeFromString(data)
    }

    suspend fun getAccount(
        address: String
    ): AccountInfo? = withContext(Dispatchers.IO) {
        return@withContext liteClient!!.getAccount(address)
    }
}