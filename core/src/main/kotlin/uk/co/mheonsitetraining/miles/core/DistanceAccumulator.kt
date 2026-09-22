package uk.co.mheonsitetraining.miles.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class Fix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Double,
    val timeMillis: Long,
)

/**
 * Adds up the distance between GPS fixes. It ignores inaccurate fixes, GPS jitter while
 * parked, and impossible jumps, so the total stays close to the odometer.
 */
class DistanceAccumulator(
    private val maxAccuracyMetres: Double = 40.0,
    private val minStepMetres: Double = 12.0,
    private val maxSpeedMetresPerSecond: Double = 70.0, // ~155 mph
    initialMetres: Double = 0.0,
) {
    var totalMetres: Double = initialMetres
        private set

    var lastAccepted: Fix? = null
        private set

    var firstAccepted: Fix? = null
        private set

    /** Returns true if the fix was used. */
    fun add(fix: Fix): Boolean {
        if (fix.accuracyMetres > maxAccuracyMetres) return false
        val previous = lastAccepted
        if (previous == null) {
            lastAccepted = fix
            if (firstAccepted == null) firstAccepted = fix
            return true
        }
        val step = haversineMetres(previous.latitude, previous.longitude, fix.latitude, fix.longitude)
        // Ignore movement smaller than the combined uncertainty of the two fixes.
        val jitter = max(minStepMetres, (previous.accuracyMetres + fix.accuracyMetres) / 2)
        if (step < jitter) return false
        val seconds = (fix.timeMillis - previous.timeMillis) / 1000.0
        if (seconds > 0 && step / seconds > maxSpeedMetresPerSecond) return false
        totalMetres += step
        lastAccepted = fix
        return true
    }

    companion object {
        private const val EARTH_RADIUS_METRES = 6_371_008.8

        fun haversineMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * EARTH_RADIUS_METRES * asin(sqrt(a.coerceIn(0.0, 1.0)))
        }
    }
}
