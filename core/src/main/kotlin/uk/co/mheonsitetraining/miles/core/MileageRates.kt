package uk.co.mheonsitetraining.miles.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * HMRC Approved Mileage Allowance Payment rates for cars and vans:
 * 45p per mile for the first 10,000 business miles in a tax year, 25p after that.
 */
data class MileageRates(
    val firstRatePence: Double = 45.0,
    val secondRatePence: Double = 25.0,
    val thresholdMiles: Double = 10_000.0,
)

/** The allowance for one business trip, given the business miles already driven that tax year. */
data class TripClaim(
    val tripId: Long,
    val taxYear: TaxYear,
    val milesAtFirstRate: Double,
    val milesAtSecondRate: Double,
    val claimPence: Long,
) {
    val claimPounds: Double get() = claimPence / 100.0
}

data class MileageSummary(
    val tripCount: Int,
    val businessTrips: Int,
    val personalTrips: Int,
    val unclassifiedTrips: Int,
    val businessMiles: Double,
    val personalMiles: Double,
    val unclassifiedMiles: Double,
    val claimPence: Long,
) {
    val totalMiles: Double get() = businessMiles + personalMiles + unclassifiedMiles
    val claimPounds: Double get() = claimPence / 100.0
}

object ClaimCalculator {

    /**
     * Works out the allowance for every business trip. The 10,000 mile threshold is applied
     * per tax year in date order, so pass *all* business trips for the tax years concerned
     * (not a filtered subset) to get the correct split.
     */
    fun claims(
        trips: List<TripRecord>,
        rates: MileageRates,
        zone: java.time.ZoneId,
    ): Map<Long, TripClaim> {
        val result = LinkedHashMap<Long, TripClaim>()
        trips.asSequence()
            .filter { it.category == TripCategory.BUSINESS }
            .sortedWith(compareBy({ it.startMillis }, { it.id }))
            .groupBy { TaxYear.ofMillis(it.startMillis, zone) }
            .forEach { (taxYear, yearTrips) ->
                var milesSoFar = 0.0
                for (trip in yearTrips) {
                    val miles = max(0.0, trip.miles)
                    val remainingAtFirstRate = max(0.0, rates.thresholdMiles - milesSoFar)
                    val atFirst = min(miles, remainingAtFirstRate)
                    val atSecond = miles - atFirst
                    val pence = atFirst * rates.firstRatePence + atSecond * rates.secondRatePence
                    result[trip.id] = TripClaim(trip.id, taxYear, atFirst, atSecond, pence.roundToLong())
                    milesSoFar += miles
                }
            }
        return result
    }

    fun summarise(trips: List<TripRecord>, claims: Map<Long, TripClaim>): MileageSummary {
        fun milesOf(category: TripCategory) = trips.filter { it.category == category }.sumOf { it.miles }
        fun countOf(category: TripCategory) = trips.count { it.category == category }
        return MileageSummary(
            tripCount = trips.size,
            businessTrips = countOf(TripCategory.BUSINESS),
            personalTrips = countOf(TripCategory.PERSONAL),
            unclassifiedTrips = countOf(TripCategory.UNCLASSIFIED),
            businessMiles = milesOf(TripCategory.BUSINESS),
            personalMiles = milesOf(TripCategory.PERSONAL),
            unclassifiedMiles = milesOf(TripCategory.UNCLASSIFIED),
            claimPence = trips.sumOf { claims[it.id]?.claimPence ?: 0L },
        )
    }
}
