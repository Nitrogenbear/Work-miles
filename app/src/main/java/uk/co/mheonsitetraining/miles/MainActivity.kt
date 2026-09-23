package uk.co.mheonsitetraining.miles

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import uk.co.mheonsitetraining.miles.data.AppSettings
import uk.co.mheonsitetraining.miles.tracking.CarConnectionReceiver
import uk.co.mheonsitetraining.miles.tracking.CarPresence
import uk.co.mheonsitetraining.miles.tracking.EventLog
import uk.co.mheonsitetraining.miles.tracking.TripService
import uk.co.mheonsitetraining.miles.ui.MheTheme
import uk.co.mheonsitetraining.miles.ui.MilesApp
import uk.co.mheonsitetraining.miles.ui.MilesViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MilesViewModel by viewModels()

    /** Trip to open for editing, e.g. after tapping the end-of-trip notification. */
    private val openTripId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MheTheme {
                MilesApp(
                    viewModel = viewModel,
                    openTripId = openTripId.value,
                    onTripOpened = { openTripId.value = null },
                )
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        startIfInCar()
    }

    /**
     * Backup for missed Bluetooth broadcasts: if the app is opened while the car is connected
     * and nothing is recording, start now (Android always allows this from the foreground).
     */
    private fun startIfInCar() {
        val settings = AppSettings.get(this)
        // If the broadcasts already saw the car, the recorder has either started or been ended by hand.
        if (!settings.current.autoTracking || settings.current.carAddress == null || TripService.isRunning) return
        if (settings.carConnected) return
        lifecycleScope.launch {
            val inCar = CarPresence.isCarConnected(this@MainActivity) { CarConnectionReceiver.isCar(it, settings) }
            if (inCar && !TripService.isRunning) {
                EventLog.log(this@MainActivity, "App opened while connected to the car")
                settings.setCarChannel("calls", true)
                try {
                    TripService.startFromCar(this@MainActivity)
                } catch (e: Exception) {
                    EventLog.log(this@MainActivity, "Couldn't start recording: ${e.javaClass.simpleName} ${e.message ?: ""}")
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(EXTRA_START_TRIP, false)) {
            try {
                TripService.startFromCar(this)
            } catch (e: Exception) {
                Toast.makeText(this, "Couldn't start recording – check permissions in Setup", Toast.LENGTH_LONG).show()
            }
        }
        val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
        if (tripId > 0) openTripId.value = tripId
        intent.removeExtra(EXTRA_START_TRIP)
        intent.removeExtra(EXTRA_TRIP_ID)
    }

    companion object {
        const val EXTRA_TRIP_ID = "tripId"
        const val EXTRA_START_TRIP = "startTrip"
    }
}
