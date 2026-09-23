package uk.co.mheonsitetraining.miles.tracking

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uk.co.mheonsitetraining.miles.data.AppSettings

/**
 * Wakes the app when the chosen car head unit connects or disconnects over Bluetooth,
 * even if the app isn't running.
 *
 * Listens to the low-level link (ACL) plus the phone-call (HFP) and media (A2DP) connections,
 * because some phones/cars only report one of them reliably.
 */
class CarConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        val action = intent.action ?: return
        val channel = when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> "link"
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> "calls"
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> "media"
            else -> return
        }
        val connected = when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> true
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> false
            else -> when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) {
                BluetoothProfile.STATE_CONNECTED -> true
                BluetoothProfile.STATE_DISCONNECTED -> false
                else -> return // connecting / disconnecting: wait for the final state
            }
        }

        val settings = AppSettings.get(context)
        val label = describe(device)
        if (device == null || !isCar(device, settings)) {
            EventLog.log(context, "Bluetooth $channel ${if (connected) "connected" else "disconnected"}: $label (not the chosen car)")
            return
        }
        EventLog.log(context, "Car $channel ${if (connected) "connected" else "disconnected"}: $label")

        val wasConnected = settings.carConnected
        if (connected) {
            settings.setCarChannel(channel, true)
            Notifications.cancel(context, Notifications.ID_START_PROMPT)
            if (!settings.current.autoTracking) {
                EventLog.log(context, "Automatic recording is switched off in Setup")
            } else if (!wasConnected || !TripService.isRunning) {
                TripService.carConnected(context)
            }
        } else {
            // A dropped link means the car has gone, whatever the other channels last said.
            if (channel == "link") settings.clearCarChannels() else settings.setCarChannel(channel, false)
            if (!settings.carConnected) onCarGone(context)
        }
    }

    private fun onCarGone(context: Context) {
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

    companion object {
        @SuppressLint("MissingPermission")
        fun describe(device: BluetoothDevice?): String {
            if (device == null) return "unknown device"
            val name = try {
                device.name
            } catch (e: SecurityException) {
                null
            }
            return "${name ?: "?"} (${device.address})"
        }

        /** Matches on the saved Bluetooth address, or the saved name if the car changes address. */
        @SuppressLint("MissingPermission")
        fun isCar(device: BluetoothDevice, settings: AppSettings): Boolean {
            val car = settings.current
            val carAddress = car.carAddress ?: return false
            return try {
                device.address.equals(carAddress, ignoreCase = true) ||
                    (!car.carName.isNullOrBlank() && device.name == car.carName)
            } catch (e: SecurityException) {
                device.address.equals(carAddress, ignoreCase = true)
            }
        }
    }
}
