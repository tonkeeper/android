package com.tonapps.blockchain.ton.extensions

import android.util.Log
import com.tonapps.blockchain.ton.AndroidSecureRandom
import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.contract.WalletV4R2Contract
import com.tonapps.blockchain.ton.contract.WalletV5R1Contract
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.block.AddrStd
import org.ton.crypto.SecureRandom
import kotlin.random.Random

fun generateVanityAddress(
    prefix: String,
    maxAttempts: Int = 1_000_000,
    userFriendly: Boolean = true,
    urlSafe: Boolean = true,
    testOnly: Boolean = false,
    bounceable: Boolean = true
): String? {
    val log = 1000
    Log.d("AppAddress", "Generating vanity address with prefix: $prefix")
    for (attempt in 1..maxAttempts) {
        if (attempt % log == 0) {
            Log.d("AppAddress", "Attempt: $attempt")
        }

        val privateKey = PrivateKeyEd25519.generate(AndroidSecureRandom)
        val publicKey = privateKey.publicKey()
        val contract = BaseWalletContract.create(publicKey, "v5r1", testOnly)
        val address = contract.address



        val addressString = address.toString(userFriendly, urlSafe, testOnly, bounceable)
        Log.d("AppAddress", "Generated address($attempt): $addressString")

        if (addressString.contains(prefix, ignoreCase = true)) {
            return address.toWalletAddress(false)
        }
    }
    return null // If no matching address found within maxAttempts
}


fun AddrStd.toWalletAddress(testnet: Boolean): String {
    return toString(
        userFriendly = true,
        bounceable = false,
        testOnly = testnet,
    )
}

fun AddrStd.toAccountId(): String {
    return toString(
        userFriendly = false,
    ).lowercase()
}

fun String.toUserFriendly(
    wallet: Boolean = true,
    testnet: Boolean,
    bounceable: Boolean = true,
): String {
    return try {
        val addr = AddrStd(this)
        if (wallet) {
            addr.toWalletAddress(testnet)
        } else {
            addr.toString(userFriendly = true, bounceable = bounceable)
        }
    } catch (e: Exception) {
        this
    }
}

fun String.toRawAddress(): String {
    return try {
        AddrStd(this).toString(userFriendly = false).lowercase()
    } catch (e: Exception) {
        this
    }
}

fun String.isValidTonAddress(): Boolean {
    return try {
        AddrStd(this)
        true
    } catch (e: Exception) {
        false
    }
}

fun String.equalsAddress(other: String): Boolean {
    return try {
        toRawAddress().equals(other.toRawAddress(), ignoreCase = true)
    } catch (e: Throwable) {
        false
    }
}