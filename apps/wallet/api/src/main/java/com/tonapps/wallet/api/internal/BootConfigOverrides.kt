package com.tonapps.wallet.api.internal

import android.content.Context
import android.content.pm.ApplicationInfo
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.entity.PerpsAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

private const val ONE_CLICK_SWAP_ENABLED = "one_click_swap_enabled"
private const val TX_VALIDATION_ENABLED = "tx_validation_enabled"
private const val PERPS_ACCESS = "perps_access"

/**
 * Debug-only overlay for `/keys/all` flags, applied on top of the fetched boot config.
 * Set from the `bootFlags` launch extra (RootActivity.EXTRA_BOOT_FLAGS), e.g.
 * `{"one_click_swap_enabled": false, "tx_validation_enabled": false, "perps_access": "restricted"}`
 * (`perps_access` is `available|restricted|hidden`). A key it does not name keeps
 * the backend's value; a later extra replaces the earlier one whole, and `{}` clears it. A blank or
 * malformed extra changes nothing: the overlay in force stays.
 */
object BootConfigOverrides {

    private val _overlay = MutableStateFlow<JSONObject?>(null)

    /** Emits each overlay applied, so the config repository republishes what reads it. */
    val overlay: StateFlow<JSONObject?> = _overlay.asStateFlow()

    fun apply(context: Context, json: String) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            return
        }
        if (json.isBlank()) {
            return
        }
        applyOverlay(json)
    }

    internal fun applyOverlay(json: String?) {
        if (json == null) {
            _overlay.value = null
            return
        }
        try {
            _overlay.value = JSONObject(json)
        } catch (_: Throwable) {
        }
    }

    fun applyTo(config: ConfigEntity): ConfigEntity = overlaid(config, _overlay.value)

    internal fun overlaid(config: ConfigEntity, overlay: JSONObject?): ConfigEntity {
        overlay ?: return config
        return config.copy(
            flags = config.flags.copy(
                oneClickSwapEnabled = overlay.optBoolean(ONE_CLICK_SWAP_ENABLED, config.flags.oneClickSwapEnabled),
                txValidationEnabled = overlay.optBoolean(TX_VALIDATION_ENABLED, config.flags.txValidationEnabled),
            ),
            perpsAccess = PerpsAccess.of(overlay.optString(PERPS_ACCESS)) ?: config.perpsAccess,
        )
    }
}
