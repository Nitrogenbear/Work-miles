package uk.co.mheonsitetraining.miles.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.mheonsitetraining.miles.core.MileageRates

data class Settings(
    /** Bluetooth MAC address of the car head unit, e.g. the Nissan's "MY-CAR". */
    val carAddress: String? = null,
    val carName: String? = null,
    val autoTracking: Boolean = true,
    val requireCharging: Boolean = true,
    val minTripMiles: Double = 0.2,
    val vehicle: String = "",
    val rates: MileageRates = MileageRates(),
)

/** Small key-value settings, exposed as a flow so the UI updates as they change. */
class AppSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val state = MutableStateFlow(read())
    val flow: StateFlow<Settings> = state.asStateFlow()
    val current: Settings get() = state.value

    /** Whether the car's head unit is connected right now (kept up to date by broadcasts). */
    var carConnected: Boolean
        get() = prefs.getBoolean(KEY_CAR_CONNECTED, false)
        set(value) = prefs.edit().putBoolean(KEY_CAR_CONNECTED, value).apply()

    fun update(transform: (Settings) -> Settings) {
        val new = transform(current)
        prefs.edit()
            .putString("carAddress", new.carAddress)
            .putString("carName", new.carName)
            .putBoolean("autoTracking", new.autoTracking)
            .putBoolean("requireCharging", new.requireCharging)
            .putFloat("minTripMiles", new.minTripMiles.toFloat())
            .putString("vehicle", new.vehicle)
            .putFloat("firstRate", new.rates.firstRatePence.toFloat())
            .putFloat("secondRate", new.rates.secondRatePence.toFloat())
            .putFloat("threshold", new.rates.thresholdMiles.toFloat())
            .apply()
        state.value = new
    }

    private fun read(): Settings {
        val defaults = Settings()
        return Settings(
            carAddress = prefs.getString("carAddress", null),
            carName = prefs.getString("carName", null),
            autoTracking = prefs.getBoolean("autoTracking", defaults.autoTracking),
            requireCharging = prefs.getBoolean("requireCharging", defaults.requireCharging),
            minTripMiles = prefs.getFloat("minTripMiles", defaults.minTripMiles.toFloat()).toDouble(),
            vehicle = prefs.getString("vehicle", "") ?: "",
            rates = MileageRates(
                firstRatePence = prefs.getFloat("firstRate", defaults.rates.firstRatePence.toFloat()).toDouble(),
                secondRatePence = prefs.getFloat("secondRate", defaults.rates.secondRatePence.toFloat()).toDouble(),
                thresholdMiles = prefs.getFloat("threshold", defaults.rates.thresholdMiles.toFloat()).toDouble(),
            ),
        )
    }

    companion object {
        private const val KEY_CAR_CONNECTED = "carConnected"

        @Volatile
        private var instance: AppSettings? = null

        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context).also { instance = it }
            }
    }
}
