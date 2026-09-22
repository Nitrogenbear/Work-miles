package uk.co.mheonsitetraining.miles.tracking

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.BatteryManager
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.mheonsitetraining.miles.core.DistanceAccumulator
import uk.co.mheonsitetraining.miles.core.Fix
import uk.co.mheonsitetraining.miles.data.AppSettings
import uk.co.mheonsitetraining.miles.data.MilesDatabase
import uk.co.mheonsitetraining.miles.data.TripEntity

/**
 * Foreground service that records a trip with GPS.
 *
 * Lifecycle:
 *  car Bluetooth connects -> [ACTION_CAR_CONNECTED] -> waits for charging -> records
 *  car Bluetooth disconnects (or "End trip") -> saves the trip and asks "work or personal?"
 */
class TripService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val dao by lazy { MilesDatabase.get(this).trips() }
    private val settings by lazy { AppSettings.get(this) }
    private lateinit var locationClient: FusedLocationProviderClient

    private var tripId: Long? = null
    private var startMillis: Long = 0
    private var accumulator = DistanceAccumulator()
    private var lastPersistMillis = 0L
    private var lastNotifyMillis = 0L
    private var locationUpdatesOn = false
    private var powerReceiverRegistered = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { onLocation(it) }
        }
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (TripStatusStore.status.value == TripStatus.WaitingForCharge) {
                scope.launch { mutex.withLock { begin() } }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        locationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_RESUME
        if (!enterForeground()) {
            if (action == ACTION_CAR_CONNECTED) Notifications.promptToStart(this)
            stopSelf()
            return START_NOT_STICKY
        }
        scope.launch {
            mutex.withLock {
                when (action) {
                    ACTION_CAR_CONNECTED -> {
                        registerPowerReceiver()
                        if (tripId == null) {
                            if (!settings.current.requireCharging || isCharging()) {
                                begin()
                            } else {
                                setStatus(TripStatus.WaitingForCharge)
                            }
                        }
                    }
                    ACTION_START -> begin()
                    ACTION_STOP, ACTION_CAR_DISCONNECTED -> finishAndStop()
                    ACTION_RESUME -> {
                        // Android restarted the service after killing it mid-trip.
                        if (tripId == null) {
                            val open = dao.inProgress()
                            if (open != null) resume(open) else finishAndStop()
                        }
                    }
                    else -> Unit
                }
            }
        }
        return START_STICKY
    }

    private fun enterForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            Notifications.ID_TRACKING,
            Notifications.tracking(this, TripStatusStore.status.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        true
    } catch (e: Exception) {
        // Missing location permission, or Android blocked a background start.
        Log.w(TAG, "Could not start recording", e)
        false
    }

    private suspend fun begin() {
        if (tripId != null) return
        Notifications.cancel(this, Notifications.ID_START_PROMPT)
        val open = dao.inProgress()
        if (open != null) {
            if (System.currentTimeMillis() - open.startMillis < STALE_TRIP_MILLIS) {
                resume(open)
                return
            }
            TripFinisher.finishOrphan(this)
        }
        val now = System.currentTimeMillis()
        val id = dao.insert(TripEntity(startMillis = now))
        startRecording(id, now, DistanceAccumulator())
    }

    private fun resume(trip: TripEntity) {
        startRecording(trip.id, trip.startMillis, DistanceAccumulator(initialMetres = trip.distanceMetres))
    }

    private fun startRecording(id: Long, start: Long, acc: DistanceAccumulator) {
        tripId = id
        startMillis = start
        accumulator = acc
        setStatus(TripStatus.Recording(id, start, acc.totalMetres))
        startLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (locationUpdatesOn) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .setMinUpdateDistanceMeters(5f)
            .build()
        try {
            locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            locationUpdatesOn = true
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission missing", e)
        }
    }

    private fun stopLocationUpdates() {
        if (!locationUpdatesOn) return
        locationClient.removeLocationUpdates(locationCallback)
        locationUpdatesOn = false
    }

    private fun onLocation(location: Location) {
        val id = tripId ?: return
        val fix = Fix(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMetres = if (location.hasAccuracy()) location.accuracy.toDouble() else 50.0,
            timeMillis = location.time,
        )
        val wasFirst = accumulator.firstAccepted == null
        if (!accumulator.add(fix)) return

        val metres = accumulator.totalMetres
        val now = System.currentTimeMillis()
        if (wasFirst) {
            scope.launch {
                dao.get(id)?.takeIf { it.startLat == null }?.let {
                    dao.update(it.copy(startLat = fix.latitude, startLng = fix.longitude))
                }
            }
        }
        if (now - lastPersistMillis > 15_000) {
            lastPersistMillis = now
            // Saved as we go so nothing is lost if the phone kills the app mid-trip.
            scope.launch { dao.setDistance(id, metres) }
        }
        TripStatusStore.set(TripStatus.Recording(id, startMillis, metres))
        if (now - lastNotifyMillis > 5_000) {
            lastNotifyMillis = now
            Notifications.notify(this, Notifications.ID_TRACKING, Notifications.tracking(this, TripStatusStore.status.value))
        }
    }

    private suspend fun finishAndStop() {
        stopLocationUpdates()
        val id = tripId
        tripId = null
        if (id != null) {
            TripFinisher.finish(this, id, accumulator.totalMetres, accumulator.firstAccepted, accumulator.lastAccepted)
        } else {
            TripFinisher.finishOrphan(this)
        }
        setStatus(TripStatus.Idle)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun setStatus(status: TripStatus) {
        TripStatusStore.set(status)
        if (status != TripStatus.Idle) {
            Notifications.notify(this, Notifications.ID_TRACKING, Notifications.tracking(this, status))
        }
    }

    private fun isCharging(): Boolean {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        return battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    }

    private fun registerPowerReceiver() {
        if (powerReceiverRegistered) return
        ContextCompat.registerReceiver(
            this, powerReceiver, IntentFilter(Intent.ACTION_POWER_CONNECTED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        powerReceiverRegistered = true
    }

    override fun onDestroy() {
        stopLocationUpdates()
        if (powerReceiverRegistered) {
            unregisterReceiver(powerReceiver)
            powerReceiverRegistered = false
        }
        tripId?.let { id ->
            // Keep the distance so far; the trip is finished or resumed later.
            val metres = accumulator.totalMetres
            CoroutineScope(Dispatchers.IO).launch { dao.setDistance(id, metres) }
        }
        TripStatusStore.set(TripStatus.Idle)
        scope.cancel()
        isRunning = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TripService"
        private const val STALE_TRIP_MILLIS = 12 * 60 * 60 * 1000L

        const val ACTION_CAR_CONNECTED = "uk.co.mheonsitetraining.miles.CAR_CONNECTED"
        const val ACTION_CAR_DISCONNECTED = "uk.co.mheonsitetraining.miles.CAR_DISCONNECTED"
        const val ACTION_START = "uk.co.mheonsitetraining.miles.START"
        const val ACTION_STOP = "uk.co.mheonsitetraining.miles.STOP"
        private const val ACTION_RESUME = "uk.co.mheonsitetraining.miles.RESUME"

        @Volatile
        var isRunning = false
            private set

        private fun intent(context: Context, action: String) =
            Intent(context, TripService::class.java).setAction(action)

        /** Called from the Bluetooth receiver while the app may be in the background. */
        fun carConnected(context: Context) {
            try {
                ContextCompat.startForegroundService(context, intent(context, ACTION_CAR_CONNECTED))
            } catch (e: Exception) {
                // Android 12+ can refuse background starts (e.g. battery optimisation is on).
                Log.w(TAG, "Background start refused", e)
                Notifications.promptToStart(context)
            }
        }

        /** Returns false if the service wasn't running, so the caller can tidy up any open trip. */
        fun carDisconnected(context: Context): Boolean {
            if (!isRunning) return false
            return try {
                context.startService(intent(context, ACTION_CAR_DISCONNECTED))
                true
            } catch (e: Exception) {
                Log.w(TAG, "Could not deliver disconnect", e)
                false
            }
        }

        /** Start recording now, from the app. */
        fun startManually(context: Context) {
            ContextCompat.startForegroundService(context, intent(context, ACTION_START))
        }

        /** Also used by the app after a "Car connected - tap to start" notification. */
        fun startFromCar(context: Context) {
            ContextCompat.startForegroundService(context, intent(context, ACTION_CAR_CONNECTED))
        }

        fun stop(context: Context) {
            if (isRunning) context.startService(intent(context, ACTION_STOP))
        }
    }
}
