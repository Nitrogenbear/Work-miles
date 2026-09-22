package uk.co.mheonsitetraining.miles.core

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvExporterTest {

    private val zone = ZoneId.of("Europe/London")

    @Test
    fun `exports rows with claim and summary`() {
        val start = LocalDateTime.of(2025, 9, 1, 8, 30).atZone(zone).toInstant().toEpochMilli()
        val trips = listOf(
            TripRecord(1, start, start + 45 * 60_000, 23.44, TripCategory.BUSINESS,
                "Leicester, LE1", "Coalville, LE67", "Forklift course, Acme Ltd"),
        )
        val claims = ClaimCalculator.claims(trips, MileageRates(), zone)
        val csv = CsvExporter.export(trips, claims, zone, vehicle = "AB12 CDE")
        val lines = csv.split("\r\n")

        assertEquals(CsvExporter.HEADER.joinToString(","), lines[0])
        assertEquals(
            "01/09/2025,08:30,09:15,\"Leicester, LE1\",\"Coalville, LE67\",23.4,Business," +
                "\"Forklift course, Acme Ltd\",2025/26,23.4,0.0,10.55",
            lines[1],
        )
        assertTrue(lines.contains("Vehicle,AB12 CDE"))
        assertTrue(lines.contains("Mileage allowance (GBP),10.55"))
    }

    @Test
    fun `escapes quotes and formula-like text`() {
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escape("say \"hi\""))
        assertEquals("'=SUM(A1)", CsvExporter.escape("=SUM(A1)"))
        assertEquals("-12.5", CsvExporter.escape("-12.5"))
    }

    @Test
    fun `formats pounds`() {
        assertEquals("0.05", CsvExporter.formatPounds(5))
        assertEquals("1234.50", CsvExporter.formatPounds(123_450))
    }
}
