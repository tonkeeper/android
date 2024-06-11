package com.tonapps.wallet.data.account.backport.data

import androidx.collection.ArrayMap
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.extensions.color
import com.tonapps.wallet.data.account.WalletType
import org.json.JSONArray
import org.json.JSONObject

data class RNWallet(
    val name: String,
    val color: Color,
    val emoji: String,
    val identifier: String,
    val pubkey: String,
    val network: Network,
    val type: Type,
    val version: ContractVersion,
    val workchain: Int,
    val allowedDestinations: String?,
    val configPubKey: String?,
    val ledger: RNLedger?
): RNData() {

    companion object {

        private val colors = ArrayMap<Color, String>().apply {
            put(Color.SteelGray, "#293342")
            put(Color.LightSteelGray, "#424C5C")
            put(Color.Gray, "#9DA2A4")
            put(Color.LightRed, "#FF8585")
            put(Color.LightOrange, "#FFA970")
            put(Color.LightYellow, "#FFC95C")
            put(Color.LightGreen, "#85CC7A")
            put(Color.LightBlue, "#70A0FF")
            put(Color.LightAquamarine, "#6CCCF5")
            put(Color.LightPurple, "#AD89F5")
            put(Color.LightViolet, "#F57FF5")
            put(Color.LightMagenta, "#F576B1")
            put(Color.LightFireOrange, "#F57F87")
            put(Color.Red, "#FF5252")
            put(Color.Orange, "#FF8B3D")
            put(Color.Yellow, "#FFB92E")
            put(Color.Green, "#69CC5A")
            put(Color.Blue, "#528BFF")
            put(Color.Aquamarine, "#47C8FF")
            put(Color.Purple, "#925CFF")
            put(Color.Violet, "#FF5CFF")
            put(Color.Magenta, "#FF479D")
            put(Color.FireOrange, "#FF525D")
        }

        fun fromJSONArray(array: JSONArray?): List<RNWallet> {
            if (array == null) {
                return emptyList()
            }
            val list = mutableListOf<RNWallet>()
            for (i in 0 until array.length()) {
                list.add(RNWallet(array.getJSONObject(i)))
            }
            return list
        }

        fun toJSONArray(list: List<RNWallet>): JSONArray {
            val array = JSONArray()
            list.forEach { array.put(it.toJSON()) }
            return array
        }

        private fun enumType(value: String): Type {
            return Type.entries.firstOrNull { it.title.equals(value, ignoreCase = true) } ?: Type.Regular
        }

        private fun enumNetwork(value: Int): Network {
            return Network.entries.firstOrNull { it.code == value } ?: Network.Mainnet
        }

        private fun enumVersion(value: String): ContractVersion {
            return ContractVersion.entries.firstOrNull { it.code.equals(value, ignoreCase = true) } ?: ContractVersion.v5R1
        }

        private fun enumColor(value: String): Color {
            return Color.entries.firstOrNull { it.key.equals(value, ignoreCase = true) } ?: Color.SteelGray
        }

        fun resolveColor(color: Int): Color {
            return Color.entries.firstOrNull { it.int == color } ?: Color.SteelGray
        }

        val Color.hex: String
            get() = colors.getOrDefault(this, "#293342")

        val Color.int: Int
            get() = hex.color

        val Type.originalType: WalletType
            get() {
                return when (this) {
                    Type.Regular -> WalletType.Default
                    Type.Lockup -> WalletType.Lockup
                    Type.WatchOnly -> WalletType.Watch
                    Type.Signer -> WalletType.Signer
                    Type.SignerDeeplink -> WalletType.Signer
                    Type.Ledger -> WalletType.Ledger
                }
            }

        val ContractVersion.originalType: WalletVersion
            get() {
                return when (this) {
                    ContractVersion.v5R1 -> WalletVersion.UNKNOWN
                    ContractVersion.v4R2 -> WalletVersion.V4R2
                    ContractVersion.v4R1 -> WalletVersion.V4R1
                    ContractVersion.v3R2 -> WalletVersion.V3R2
                    ContractVersion.v3R1 -> WalletVersion.V3R1
                    ContractVersion.LockupV1 -> WalletVersion.UNKNOWN
                }
            }

        fun fromOriginal(originalVersion: WalletVersion): ContractVersion {
            return when (originalVersion) {
                WalletVersion.UNKNOWN -> ContractVersion.v5R1
                WalletVersion.V4R2 -> ContractVersion.v4R2
                WalletVersion.V4R1 -> ContractVersion.v4R1
                WalletVersion.V3R2 -> ContractVersion.v3R2
                WalletVersion.V3R1 -> ContractVersion.v3R1
            }
        }
    }

    enum class Type(val title: String) {
        Regular("Regular"),
        Lockup("Lockup"),
        WatchOnly("WatchOnly"),
        Signer("Signer"),
        SignerDeeplink("SignerDeeplink"),
        Ledger("Ledger")
    }

    enum class Network(val code: Int) {
        Mainnet(-239),
        Testnet(-3)
    }

    enum class ContractVersion(val code: String) {
        v5R1("v5R1"),
        v4R2("v4R2"),
        v4R1("v4R1"),
        v3R2("v3R2"),
        v3R1("v3R1"),
        LockupV1("lockup-0.1")
    }

    enum class Color(val key: String) {
        SteelGray("SteelGray"),
        LightSteelGray("LightSteelGray"),
        Gray("Gray"),
        LightRed("LightRed"),
        LightOrange("LightOrange"),
        LightYellow("LightYellow"),
        LightGreen("LightGreen"),
        LightBlue("LightBlue"),
        LightAquamarine("LightAquamarine"),
        LightPurple("LightPurple"),
        LightViolet("LightViolet"),
        LightMagenta("LightMagenta"),
        LightFireOrange("LightFireOrange"),
        Red("Red"),
        Orange("Orange"),
        Yellow("Yellow"),
        Green("Green"),
        Blue("Blue"),
        Aquamarine("Aquamarine"),
        Purple("Purple"),
        Violet("Violet"),
        Magenta("Magenta"),
        FireOrange("FireOrange"),
    }

    constructor(json: JSONObject) : this(
        json.getString("name"),
        enumColor(json.getString("color")),
        json.getString("emoji"),
        json.getString("identifier"),
        json.getString("pubkey"),
        enumNetwork(json.getInt("network")),
        enumType(json.getString("type")),
        enumVersion(json.getString("version")),
        json.getInt("workchain"),
        json.optString("allowedDestinations", null),
        json.optString("configPubKey", null),
        json.optJSONObject("ledger")?.let { RNLedger(it) }
    )

    override fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("color", color.key)
            put("emoji", emoji)
            put("identifier", identifier)
            put("pubkey", pubkey)
            put("network", network.code)
            put("type", type.title)
            put("version", version.code)
            put("workchain", workchain)
            allowedDestinations?.let { put("allowedDestinations", it) }
            configPubKey?.let { put("configPubKey", it) }
            ledger?.let { put("ledger", it.toJSON()) }
        }
    }


}
