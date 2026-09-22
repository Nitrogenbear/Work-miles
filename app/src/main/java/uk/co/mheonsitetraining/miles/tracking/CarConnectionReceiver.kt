package uk.co.mheonsitetraining.miles.tracking

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uk.co.mheonsitetraining.miles.data.AppSettings

/**
 * Wakes the app when the chosen car head unit connects or disconnects over Bluetooth,
 * even if the app isn't running.
 */
class CarConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            ?: return
        val settings = AppSettings.get(context)
        if (!isCar(device, settings)) return

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                settings.carConnected = true
                if (settings.current.autoTracking) TripService.carConnected(context)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                settings.carConnected = false
                Notifications.cancel(context, Notifications.ID_START_PROMPT)
                if (!TripService.carDisconnected(context)) {
                    // The app was closed mid-trip: close off whatever was recorded so far.
                    val pending = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            TripFinisher.finishOrphan(context)
                        } finally {
                            pending.finish()
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "CarConnection"

        fun isCar(device: BluetoothDevice, settings: AppSettings): Boolean {
            val carAddress = settings.current.carAddress ?: return false
            return try {
                device.address.equals(carAddress, ignoreCase = true)
            } catch (e: SecurityException) {
                Log.w(TAG, "No Bluetooth permission", e)
                false
            }
        }
    }
}
