package uk.co.mheonsitetraining.miles.tracking

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Asks Android directly whether the car is connected right now, and links the car to the app. */
object CarPresence {

    /**
     * True if the car is connected over Bluetooth for calls or media. Used when the app is
     * opened, in case the connection broadcast was missed.
     */
    @SuppressLint("MissingPermission")
    suspend fun isCarConnected(context: Context, isCar: (BluetoothDevice) -> Boolean): Boolean {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        if (!adapter.isEnabled) return false
        for (profile in listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP)) {
            val found = withTimeoutOrNull(3_000) {
                suspendCancellableCoroutine { cont ->
                    val listener = object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                            val match = try {
                                proxy.connectedDevices.any(isCar)
                            } catch (e: SecurityException) {
                                false
                            }
                            adapter.closeProfileProxy(p, proxy)
                            if (cont.isActive) cont.resume(match)
                        }

                        override fun onServiceDisconnected(p: Int) = Unit
                    }
                    val started = try {
                        adapter.getProfileProxy(context, listener, profile)
                    } catch (e: SecurityException) {
                        false
                    }
                    if (!started && cont.isActive) cont.resume(false)
                }
            } ?: false
            if (found) return true
        }
        return false
    }

    private fun manager(context: Context): CompanionDeviceManager? =
        if (context.packageManager.hasSystemFeature("android.software.companion_device_setup")) {
            context.getSystemService(CompanionDeviceManager::class.java)
        } else {
            null
        }

    /**
     * Whether the car is linked as a "companion device". Android then allows the app to start
     * recording from the background when the car connects, and won't put it to sleep.
     */
    fun isLinked(context: Context, address: String?): Boolean {
        if (address == null) return false
        val cdm = manager(context) ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                cdm.myAssociations.any { it.deviceMacAddress?.toString().equals(address, ignoreCase = true) }
            } else {
                @Suppress("DEPRECATION")
                cdm.associations.any { it.equals(address, ignoreCase = true) }
            }
        } catch (e: Exception) {
            false
        }
    }

    fun canLink(context: Context) = manager(context) != null

    /** Shows Android's "link this device" dialog for the car. */
    fun link(context: Context, address: String, onShowDialog: (IntentSender) -> Unit, onError: (String) -> Unit) {
        val cdm = manager(context) ?: return onError("This phone doesn't support linking devices")
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address.uppercase()).build())
            .setSingleDevice(true)
            .build()
        val callback = object : CompanionDeviceManager.Callback() {
            @Deprecated("Replaced by onAssociationPending on Android 13+")
            override fun onDeviceFound(chooserLauncher: IntentSender) = onShowDialog(chooserLauncher)

            override fun onAssociationPending(intentSender: IntentSender) = onShowDialog(intentSender)

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                EventLog.log(context, "Car linked to the app")
            }

            override fun onFailure(error: CharSequence?) = onError(error?.toString() ?: "Couldn't find the car")
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                cdm.associate(request, context.mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                cdm.associate(request, callback, Handler(Looper.getMainLooper()))
            }
        } catch (e: Exception) {
            onError(e.message ?: "Couldn't link the car")
        }
    }
}
