package uk.co.mheonsitetraining.miles.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaxYearTest {

    @Test
    fun `5 April belongs to the previous tax year and 6 April starts a new one`() {
        assertEquals(TaxYear(2024), TaxYear.of(LocalDate.of(2025, 4, 5)))
        assertEquals(TaxYear(2025), TaxYear.of(LocalDate.of(2025, 4, 6)))
        assertEquals(TaxYear(2025), TaxYear.of(LocalDate.of(2025, 12, 31)))
        assertEquals(TaxYear(2025), TaxYear.of(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `label and bounds`() {
        val year = TaxYear(2025)
        assertEquals("2025/26", year.label)
        assertEquals("1999/00", TaxYear(1999).label)
        assertTrue(year.contains(LocalDate.of(2025, 4, 6)))
        assertTrue(year.contains(LocalDate.of(2026, 4, 5)))
        assertFalse(year.contains(LocalDate.of(2026, 4, 6)))
    }

    @Test
    fun `millis bounds use the local zone`() {
        val zone = ZoneId.of("Europe/London")
        val year = TaxYear(2025)
        // 6 April is in BST, so midnight local time is 23:00 UTC the day before.
        assertEquals(java.time.Instant.parse("2025-04-05T23:00:00Z").toEpochMilli(), year.startMillis(zone))
        assertEquals(TaxYear(2026).startMillis(zone), year.endMillisExclusive(zone))
    }
}
