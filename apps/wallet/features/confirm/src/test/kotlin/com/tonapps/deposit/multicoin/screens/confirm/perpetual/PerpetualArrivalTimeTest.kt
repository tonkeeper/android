package com.tonapps.deposit.multicoin.screens.confirm.perpetual

import org.junit.Assert.assertEquals
import org.junit.Test

class PerpetualArrivalTimeTest {

    @Test
    fun arrivalRoundsUpToWholeMinutesAndIsAtLeastOne() {
        assertEquals(1L, arrivalMinutes(0L))
        assertEquals(1L, arrivalMinutes(1L))
        assertEquals(1L, arrivalMinutes(60L))
        assertEquals(2L, arrivalMinutes(61L))
        assertEquals(2L, arrivalMinutes(115L))
        assertEquals(18L, arrivalMinutes(1072L))
    }

    @Test
    fun depositArrivalRoundsToTheNearestMinuteAndIsAtLeastOne() {
        assertEquals(1, approximateArrivalMinutes(0L))
        assertEquals(1, approximateArrivalMinutes(89L))
        assertEquals(2, approximateArrivalMinutes(90L))
        assertEquals(2, approximateArrivalMinutes(146L))
        assertEquals(3, approximateArrivalMinutes(150L))
    }
}
