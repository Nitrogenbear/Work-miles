package uk.co.mheonsitetraining.miles.tracking

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Turns coordinates into a short address such as "High Street, Coalville LE67 3AB". */
object Geocoding {

    suspend fun describe(context: Context, lat: Double?, lng: Double?): String {
        if (lat == null || lng == null) return ""
        val fallback = String.format(Locale.UK, "%.5f, %.5f", lat, lng)
        if (!Geocoder.isPresent()) return fallback
        val address = withTimeoutOrNull(15_000) { lookup(context, lat, lng) } ?: return fallback
        return format(address).ifBlank { fallback }
    }

    private suspend fun lookup(context: Context, lat: Double, lng: Double): Address? {
        val geocoder = Geocoder(context, Locale.UK)
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (cont.isActive) cont.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(null)
                        }
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun format(address: Address): String {
        val street = address.thoroughfare
        val town = address.locality ?: address.subAdminArea
        val postcode = address.postalCode
        val parts = listOfNotNull(street, listOfNotNull(town, postcode).joinToString(" ").ifBlank { null })
        return parts.joinToString(", ").ifBlank { address.getAddressLine(0) ?: "" }
    }
}
