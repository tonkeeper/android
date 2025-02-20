package com.tonapps.tonkeeper.ui.screen.dev

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.extensions.bestMessage
import com.tonapps.tonkeeper.extensions.requestVault
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.card.CardScreen
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.rn.RNLegacy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.ton.mnemonic.Mnemonic
import uikit.extensions.activity

class DevViewModel(
    app: Application,
    private val rnLegacy: RNLegacy,
    private val accountRepository: AccountRepository,
    private val dAppsRepository: DAppsRepository,
): BaseWalletVM(app) {

    fun getLegacyStorage(callback: (result: String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            var value = rnLegacy.getAllKeyValuesForDebug().toString()
            if (6 >= value.length) {
                value = accountRepository.getVaultKeys()
            }
            val oldJSON = try {
                JSONObject(value)
            } catch (e: Throwable) {
                JSONObject()
            }

            val newJSON = maskValues(oldJSON)
            withContext(Dispatchers.Main) {
                callback(newJSON.toString())
            }
        }
    }

    private fun maskValues(input: JSONObject): JSONObject {
        val output = JSONObject()
        for (key in input.keys()) {
            when (val value = input.get(key)) {
                is String -> output.put(key, maskTextAtIntervals(value))
                is JSONObject -> output.put(key, maskValues(value))
                is JSONArray -> output.put(key, maskValues(value))
                else -> output.put(key, value)
            }
        }
        return output
    }

    private fun maskValues(input: JSONArray): JSONArray {
        val output = JSONArray()
        for (i in 0 until input.length()) {
            when (val value = input.get(i)) {
                is String -> output.put(maskTextAtIntervals(value))
                is JSONObject -> output.put(maskValues(value))
                is JSONArray -> output.put(maskValues(value))
                else -> output.put(value)
            }
        }
        return output
    }

    private fun maskTextAtIntervals(input: String): String {
        val interval = 4
        val asterisks = "****"
        val builder = StringBuilder(input)

        var position = input.length - (input.length % interval) - 1
        while (position >= 0) {
            builder.replace(position, position + 3, asterisks)
            position -= interval
        }

        return builder.toString()
    }


    fun openCard() {
        accountRepository.selectedWalletFlow.take(1).onEach {
            openScreen(CardScreen.newInstance(it))
            finish()
        }.launch()
    }

    fun importApps(callback: (result: String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val lines = mutableListOf<String>()
            try {
                val tcApps = rnLegacy.getTCApps()
                lines.add("Mainnet apps found: ${tcApps.mainnet.size}")

                if (tcApps.mainnet.isNotEmpty()) {
                    for (apps in tcApps.mainnet) {
                        dAppsRepository.migrationFromLegacy(apps, false)
                        lines.add("Mainnet app imported: ${apps.address}")
                    }
                }

                lines.add("\n")
                lines.add("Testnet apps found: ${tcApps.testnet.size}")
                if (tcApps.testnet.isNotEmpty()) {
                    for (apps in tcApps.testnet) {
                        dAppsRepository.migrationFromLegacy(apps, true)
                        lines.add("Testnet app imported: ${apps.address}")
                    }
                }
            } catch (e: Throwable) {
                FirebaseCrashlytics.getInstance().recordException(e)
                lines.add(e.bestMessage)
                systemLongToast("Exception: ${e.bestMessage}")
            }
            withContext(Dispatchers.Main) {
                callback(lines.joinToString("\n"))
            }
        }
    }

    fun importPasscode(callback: () -> Unit) {
        viewModelScope.launch {
            val passcode = try {
                rnLegacy.exportPasscodeWithBiometry()
            } catch (e: Throwable) {
                FirebaseCrashlytics.getInstance().recordException(e)
                systemLongToast("Exception: ${e.bestMessage}")
                null
            }
            systemLongToast("Passcode: $passcode")
            withContext(Dispatchers.Main) {
                callback()
            }
        }
    }

    fun importMnemonicAgain(withDisplayMnemonic: Boolean, callback: (result: String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val lines = mutableListOf<String>()
            try {
                val wallets = rnLegacy.getWallets()
                if (wallets.count == 0) {
                    lines.add("No wallets to import")
                } else {
                    lines.add("Wallets found: ${wallets.count}")
                    lines.add("\n")
                    for (wallet in wallets.wallets) {
                        lines.add("Wallet: ${wallet.identifier}")
                        lines.add("Public key: ${wallet.pubkey}")
                        lines.add("\n")
                    }
                    val activity = context.activity ?: throw IllegalStateException("Activity not found")
                    val vaultState = rnLegacy.requestVault(activity)
                    if (vaultState.keys.isEmpty) {
                        lines.add("No keys in vault")
                    } else {
                        for ((walletId, decryptedData) in vaultState.keys) {
                            val parsedMnemonic = parseMnemonic(decryptedData.mnemonic)
                            if (Mnemonic.isValid(parsedMnemonic)) {
                                accountRepository.addMnemonic(parsedMnemonic)
                                lines.add("Valid mnemonic added to vault: $walletId")
                            } else {
                                lines.add("Invalid mnemonic for wallet $walletId")
                            }
                            if (withDisplayMnemonic) {
                                lines.add("Mnemonic: ${decryptedData.mnemonic}")
                            }
                            lines.add("\n\n")
                        }
                    }
                }
            } catch (e: Throwable) {
                FirebaseCrashlytics.getInstance().recordException(e)
                lines.add(e.bestMessage)
                systemLongToast("Exception: ${e.bestMessage}")
            }
            withContext(Dispatchers.Main) {
                callback(lines.joinToString("\n"))
            }
        }
    }

    private fun parseMnemonic(mnemonic: String): List<String> {
        return if (mnemonic.contains(",")) {
            mnemonic.split(",")
        } else if (mnemonic.contains(" ")) {
            mnemonic.split(" ")
        } else {
            mnemonic.split("\n")
        }.map { it.trim() }
    }

    private suspend fun systemLongToast(message: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}