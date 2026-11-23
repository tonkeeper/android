package com.tonapps.deposit.testing

import android.icu.text.DecimalFormat
import android.icu.text.NumberFormat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import java.math.BigDecimal
import java.util.Locale

// Mocking the DecimalFormat constructor again drops the stubs of the instance CurrencyFormatter keeps.
object PlatformNumberFormat {

    private var installed = false

    @Synchronized
    fun stub() {
        if (installed) {
            return
        }
        installed = true
        mockkStatic(NumberFormat::class)
        every { NumberFormat.getCurrencyInstance(any<Locale>()) } returns mockk<DecimalFormat>(relaxed = true) {
            every { toPattern() } returns "¤#,##0.00"
        }
        mockkConstructor(DecimalFormat::class)
        every { anyConstructed<DecimalFormat>().format(any<BigDecimal>()) } answers { firstArg<BigDecimal>().toPlainString() }
    }
}
