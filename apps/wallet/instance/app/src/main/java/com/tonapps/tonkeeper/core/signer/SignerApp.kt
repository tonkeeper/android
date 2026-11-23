package com.tonapps.tonkeeper.core.signer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.tonkeeper.helper.BrowserHelper
import org.ton.kotlin.crypto.PublicKeyEd25519
import org.ton.cell.Cell

object SignerApp {

    private const val SIGNER_PACKAGE = "com.tonapps.signer"
    private const val PLAY_STORE_PACKAGE = "com.android.vending"
    private const val SIGNER_DEEPLINK = "tonsign://v1/"
    private const val SIGNER_MARKET_URL = "market://details?id=$SIGNER_PACKAGE"
    private const val SIGNER_STORE_URL = "https://play.google.com/store/apps/details?id=$SIGNER_PACKAGE"

    fun openAppOrInstall(context: Context) {
        if (startActivitySafe(context, Intent(Intent.ACTION_VIEW, SIGNER_DEEPLINK.toUri()))) {
            return
        }
        val marketIntent = Intent(Intent.ACTION_VIEW, SIGNER_MARKET_URL.toUri()).apply {
            setPackage(PLAY_STORE_PACKAGE)
        }
        if (startActivitySafe(context, marketIntent)) {
            return
        }
        BrowserHelper.open(context, SIGNER_STORE_URL.toUri())
    }

    private fun startActivitySafe(context: Context, intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Throwable) {
            false
        }
    }

    fun createSignUri(
        body: Cell,
        publicKey: PublicKeyEd25519,
        walletVersion: WalletVersion,
        network: TonNetwork,
    ): Uri {
        val builder = SIGNER_DEEPLINK.toUri().buildUpon()
            .appendQueryParameter("network", "ton")
            .appendQueryParameter("pk", publicKey.hex())
            .appendQueryParameter("body", body.hex())
            .appendQueryParameter("tn", network.value.toString())
        tonsignVersion(walletVersion)?.let { builder.appendQueryParameter("v", it) }
        return builder.build()
    }

    private fun tonsignVersion(walletVersion: WalletVersion): String? {
        return when (walletVersion) {
            WalletVersion.V3R1 -> "v3r1"
            WalletVersion.V3R2 -> "v3r2"
            WalletVersion.V4R1 -> "v4r1"
            WalletVersion.V4R2 -> "v4r2"
            WalletVersion.V5BETA -> "v5beta"
            WalletVersion.V5R1 -> "v5r1"
            WalletVersion.WALLET_TG, WalletVersion.UNKNOWN -> null
        }
    }
}
