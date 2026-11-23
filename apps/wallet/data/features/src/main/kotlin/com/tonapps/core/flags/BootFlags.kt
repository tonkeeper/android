package com.tonapps.core.flags

/**
 * Flags of the boot config (`/keys/all` → `flags`) that features read without a reference to the API.
 * The config repository publishes them in one [update] each time it sets a config; until the first one
 * they hold [Values.Safe]: one-click swap off, so the classic confirm shows, and validation on, so
 * nothing is signed unchecked.
 */
object BootFlags {

    /** One config's flags, published together so a reader never sees half of one config and half of another. */
    data class Values(
        /** `one_click_swap_enabled`: the swap screen confirms and sends the swap in place, without the confirm screen. */
        val oneClickSwap: Boolean,
        /** `tx_validation_enabled`: chain-kit's `validate` before an operation is signed; off, the confirm shows no findings. */
        val txValidation: Boolean,
    ) {
        companion object {
            val Safe = Values(oneClickSwap = false, txValidation = true)
        }
    }

    @Volatile
    var values: Values = Values.Safe
        private set

    val isOneClickSwapEnabled: Boolean
        get() = values.oneClickSwap

    val isTxValidationEnabled: Boolean
        get() = values.txValidation

    fun update(values: Values) {
        this.values = values
    }

    fun reset() = update(Values.Safe)
}
