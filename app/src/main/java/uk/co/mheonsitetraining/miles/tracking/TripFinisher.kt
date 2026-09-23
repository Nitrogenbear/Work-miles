package uk.co.mheonsitetraining.miles.tracking

import android.content.Context
import uk.co.mheonsitetraining.miles.core.Fix
import uk.co.mheonsitetraining.miles.core.metresToMiles
import uk.co.mheonsitetraining.miles.data.AppSettings
import uk.co.mheonsitetraining.miles.data.MilesDatabase

/** Closes off a recorded trip: saves where it ended, looks up addresses and asks "work or personal?". */
object TripFinisher {

    suspend fun finish(
        context: Context,
        tripId: Long,
        metres: Double,
        firstFix: Fix?,
        lastFix: Fix?,
        endMillis: Long = System.currentTimeMillis(),
    ) {
        val dao = MilesDatabase.get(context).trips()
        val trip = dao.get(tripId) ?: return
        if (!trip.inProgress) return

        val minMiles = AppSettings.get(context).current.minTripMiles
        if (metresToMiles(metres) < minMiles) {
            // Engine on but car never really moved (e.g. sat on the drive) - not worth keeping.
            EventLog.log(context, "Trip discarded: shorter than the $minMiles mile minimum")
            dao.delete(trip)
            return
        }

        val startLat = trip.startLat ?: firstFix?.latitude
        val startLng = trip.startLng ?: firstFix?.longitude
        val endLat = lastFix?.latitude ?: trip.endLat
        val endLng = lastFix?.longitude ?: trip.endLng
        val finished = trip.copy(
            endMillis = endMillis,
            distanceMetres = metres,
            startLat = startLat,
            startLng = startLng,
            endLat = endLat,
            endLng = endLng,
            fromAddress = trip.fromAddress.ifBlank { Geocoding.describe(context, startLat, startLng) },
            toAddress = trip.toAddress.ifBlank { Geocoding.describe(context, endLat, endLng) },
        )
        dao.update(finished)
        Notifications.askForCategory(context, finished)
    }

    /** Finishes a trip left open because Android stopped the app mid-journey. */
    suspend fun finishOrphan(context: Context) {
        val trip = MilesDatabase.get(context).trips().inProgress() ?: return
        finish(context, trip.id, trip.distanceMetres, firstFix = null, lastFix = null)
    }
}
