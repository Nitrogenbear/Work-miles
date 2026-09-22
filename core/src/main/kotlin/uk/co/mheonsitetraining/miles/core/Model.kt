package uk.co.mheonsitetraining.miles.core

enum class TripCategory(val label: String) {
    UNCLASSIFIED("Not yet classified"),
    BUSINESS("Business"),
    PERSONAL("Personal"),
}

/** A finished trip, independent of how it is stored on the phone. */
data class TripRecord(
    val id: Long,
    val startMillis: Long,
    val endMillis: Long,
    val miles: Double,
    val category: TripCategory,
    val fromAddress: String = "",
    val toAddress: String = "",
    val purpose: String = "",
)

const val METRES_PER_MILE = 1609.344

fun metresToMiles(metres: Double): Double = metres / METRES_PER_MILE

fun milesToMetres(miles: Double): Double = miles * METRES_PER_MILE
