package com.yourteam.expensetracker.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ReminderTimeTest {
    private fun at(h: Int, m: Int) = LocalDateTime.of(2026, 11, 9, h, m)

    @Test fun afternoonWaitsUntilNinePm() {
        assertEquals(3 * 3600L, PaymentCheck.secondsUntilEvening(at(18, 0)))
    }

    @Test fun lateEveningRemindsInHalfAnHour() {
        assertEquals(30 * 60L, PaymentCheck.secondsUntilEvening(at(20, 45)))
        assertEquals(30 * 60L, PaymentCheck.secondsUntilEvening(at(23, 10)))
    }
}
