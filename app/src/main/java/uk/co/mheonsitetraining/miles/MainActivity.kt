package uk.co.mheonsitetraining.miles

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
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
