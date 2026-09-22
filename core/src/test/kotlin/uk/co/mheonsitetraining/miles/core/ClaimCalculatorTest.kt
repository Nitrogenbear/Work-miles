package uk.co.mheonsitetraining.miles.core

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class ClaimCalculatorTest {

    private val zone = ZoneId.of("Europe/London")

    private fun trip(id: Long, date: LocalDateTime, miles: Double, category: TripCategory = TripCategory.BUSINESS) =
        TripRecord(
            id = id,
            startMillis = date.atZone(zone).toInstant().toEpochMilli(),
            endMillis = date.plusHours(1).atZone(zone).toInstant().toEpochMilli(),
            miles = miles,
            category = category,
        )

    @Test
    fun `business miles are paid at 45p up to 10000 then 25p`() {
        val trips = listOf(
            trip(1, LocalDateTime.of(2025, 5, 1, 9, 0), 9_990.0),
            trip(2, LocalDateTime.of(2025, 6, 1, 9, 0), 20.0), // straddles the threshold
            trip(3, LocalDateTime.of(2025, 7, 1, 9, 0), 100.0),
            trip(4, LocalDateTime.of(2025, 7, 2, 9, 0), 500.0, TripCategory.PERSONAL),
        )
        val claims = ClaimCalculator.claims(trips, MileageRates(), zone)

        assertEquals(449_550L, claims.getValue(1).claimPence)
        assertEquals(10.0, claims.getValue(2).milesAtFirstRate)
        assertEquals(10.0, claims.getValue(2).milesAtSecondRate)
        assertEquals(700L, claims.getValue(2).claimPence)
        assertEquals(2_500L, claims.getValue(3).claimPence)
        assertEquals(null, claims[4])

        val summary = ClaimCalculator.summarise(trips, claims)
        assertEquals(10_110.0, summary.businessMiles)
        assertEquals(500.0, summary.personalMiles)
        assertEquals(452_750L, summary.claimPence)
    }

    @Test
    fun `threshold resets each tax year`() {
        val trips = listOf(
            trip(1, LocalDateTime.of(2025, 4, 5, 9, 0), 10_000.0),
            trip(2, LocalDateTime.of(2025, 4, 6, 9, 0), 10.0),
        )
        val claims = ClaimCalculator.claims(trips, MileageRates(), zone)
        assertEquals(TaxYear(2024), claims.getValue(1).taxYear)
        assertEquals(TaxYear(2025), claims.getValue(2).taxYear)
        assertEquals(450L, claims.getValue(2).claimPence)
    }
}
