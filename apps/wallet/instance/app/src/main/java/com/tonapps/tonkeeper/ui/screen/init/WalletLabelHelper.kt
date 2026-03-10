package com.tonapps.tonkeeper.ui.screen.init

import android.content.Context
import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import com.tonapps.blockchain.tron.isValidTronAddress
import com.tonapps.emoji.Emoji
import com.tonapps.tonkeeper.extensions.consistentBucketFor
import com.tonapps.tonkeeper.extensions.isNullOrEmpty
import com.tonapps.wallet.data.account.WalletColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.PairSerializer

internal class WalletLabelHelper(private val context: Context) {

    private fun hashSeq(items: List<String>): Long {
        var h = -0x340d631b5b9d5f1bL
        val p = 0x100000001b3L
        for (s in items) {
            h = (h xor 0xFFL) * p
            for (ch in s) {
                val hi = (ch.code ushr 8) and 0xFF
                val lo = ch.code and 0xFF
                h = (h xor hi.toLong()) * p
                h = (h xor lo.toLong()) * p
            }
        }
        return h
    }

    private fun jumpConsistentHash(key: Long, buckets: Int): Int {
        var b = -1
        var j = 0
        var k = key
        while (j < buckets) {
            b = j
            k = k * 2862933555777941757L + 1
            val denom = ((k ushr 33) + 1).toDouble()
            j = (((b + 1).toLong() * (1L shl 31)) / denom).toInt()
        }
        return b
    }

    private fun generateEmojiNames(
        names: List<String>
    ) = names.filter { !it.isValidTonAddress() && !it.isValidTronAddress() }
        .map {
            it.replace(".t.me", "").replace(".ton", "")
        }.map { it.split(".") }.flatten()
        .map { it.split("-") }.flatten()
        .map { it.split(" ") }.flatten()
        .mapNotNull { it.trim().ifBlank { null } }.sorted()

    private suspend fun findEmojiByNames(names: List<String>): CharSequence? {
        val emojiNames = generateEmojiNames(names)
        return Emoji.findByNames(context, emojiNames).firstOrNull()?.value
    }

    private suspend fun findEmojiByPrivateKey(bytes: ByteArray): CharSequence {
        val emojis = Emoji.get(context).filter { !it.custom }
        val emojiIndex = bytes.consistentBucketFor(emojis.size)
        return emojis[emojiIndex].value
    }

    private suspend fun emoji(names: List<String>, bytes: ByteArray?): CharSequence? {
        val emojiByNames = findEmojiByNames(names)
        if (!emojiByNames.isNullOrBlank()) {
            return emojiByNames
        }
        return bytes?.let {
            findEmojiByPrivateKey(it)
        }
    }

    private fun findColorByNames(names: List<String>): Int? {
        if (names.isEmpty()) {
            return null
        }
        val hash = hashSeq(names)
        val index = jumpConsistentHash(hash, WalletColor.all.size)
        if (index == 0) {
            return null
        }
        return WalletColor.all[index]
    }

    private fun findColorByPrivateKey(bytes: ByteArray): Int {
        val colorIndex = bytes.consistentBucketFor(WalletColor.all.size)
        return WalletColor.all[colorIndex]
    }

    private fun color(names: List<String>, bytes: ByteArray?): Int? {
        val color = findColorByNames(names)
        if (color != null) {
            return color
        }
        return bytes?.let {
            findColorByPrivateKey(it)
        }
    }

    fun parseNameAndEmoji(names: List<String>): Pair<String?, String?> {
        var name = names.firstNotNullOfOrNull { it.trim().ifBlank { null } }
        val emoji = name?.let {
            Emoji.getEmojiFromPrefix(it)
        }
        name = name?.replace(emoji.toString(), "")?.trim()?.ifBlank { null }
        return Pair(name, emoji)
    }

    suspend fun generate(
        names: List<String>,
        bytes: ByteArray?
    ) = withContext(Dispatchers.IO) {
        /*if (names.isEmpty() && bytes.isNullOrEmpty()) {
            Pair(null, null)
        } else {
            val emoji = emoji(names, bytes)
            val color = color(names, bytes)
            Pair(emoji, color)
        }*/
        Pair(Emoji.WALLET_ICON, WalletColor.all.first())
    }
}