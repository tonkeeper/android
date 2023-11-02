package ton.console.model

import android.util.ArrayMap
import ton.SupportedCurrency
import org.json.JSONObject

data class RatesDiffModel(
    val values: ArrayMap<String, String>
) {

    companion object {
        fun parse(json: JSONObject): RatesDiffModel {
            val values = ArrayMap<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                values[key] = json.getString(key)
            }
            return RatesDiffModel(values)
        }
    }

    fun get(supportedCurrency: SupportedCurrency): String {
        return get(supportedCurrency.code)
    }

    fun get(key: String): String {
        return values[key.uppercase()] ?: ""
    }
}