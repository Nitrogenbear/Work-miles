package uk.co.mheonsitetraining.miles.tracking

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import uk.co.mheonsitetraining.miles.MainActivity
import uk.co.mheonsitetraining.miles.R
import uk.co.mheonsitetraining.miles.core.CsvExporter
import uk.co.mheonsitetraining.miles.core.TripCategory
import uk.co.mheonsitetraining.miles.data.TripEntity

object Notifications {
    const val CHANNEL_TRACKING = "tracking"
    const val CHANNEL_TRIPS = "trips"

    const val ID_TRACKING = 1
    const val ID_START_PROMPT = 2
    private const val ID_TRIP_BASE = 10_000

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TRACKING, "Trip recording", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while a trip is being recorded"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TRIPS, "Finished trips", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Asks whether a finished trip was for work"
            },
        )
    }

    fun tripNotificationId(tripId: Long): Int = ID_TRIP_BASE + (tripId % 1_000_000).toInt()

    private fun openAppIntent(context: Context, tripId: Long? = null, startTrip: Boolean = false): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (tripId != null) putExtra(MainActivity.EXTRA_TRIP_ID, tripId)
            if (startTrip) putExtra(MainActivity.EXTRA_START_TRIP, true)
        }
        val requestCode = when {
            startTrip -> 1
            tripId != null -> tripNotificationId(tripId)
            else -> 0
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun tracking(context: Context, status: TripStatus): android.app.Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_stat_trip)
            .setColor(ContextCompat.getColor(context, R.color.mhe_orange))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(openAppIntent(context))
        when (status) {
            is TripStatus.Recording -> {
                builder.setContentTitle("Recording trip")
                    .setContentText("${CsvExporter.formatMiles(status.metres / 1609.344)} miles so far")
                    .setUsesChronometer(true)
                    .setWhen(status.startMillis)
                    .addAction(0, "End trip", serviceIntent(context, TripService.ACTION_STOP))
            }
            TripStatus.WaitingForCharge -> builder.setContentTitle("Car connected")
                .setContentText("Recording starts when the phone begins charging")
                .addAction(0, "Start now", serviceIntent(context, TripService.ACTION_START))
            TripStatus.Idle -> builder.setContentTitle("MHE Miles")
                .setContentText("Getting ready…")
        }
        return builder.build()
    }

    private fun serviceIntent(context: Context, action: String): PendingIntent =
        PendingIntent.getForegroundService(
            context, action.hashCode(),
            Intent(context, TripService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** The end-of-trip question: "Was this trip for work?" */
    fun askForCategory(context: Context, trip: TripEntity) {
        val title = "Trip finished · ${CsvExporter.formatMiles(trip.miles)} miles"
        val route = listOf(trip.fromAddress, trip.toAddress).filter { it.isNotBlank() }.joinToString(" → ")
        val notification = NotificationCompat.Builder(context, CHANNEL_TRIPS)
            .setSmallIcon(R.drawable.ic_stat_trip)
            .setColor(ContextCompat.getColor(context, R.color.mhe_orange))
            .setContentTitle(title)
            .setContentText(if (route.isNotBlank()) "Was this for work? $route" else "Was this trip for work?")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Was this trip for work?" + if (route.isNotBlank()) "\n$route" else "",
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, trip.id))
            .addAction(0, "Work", ClassifyReceiver.intent(context, trip.id, TripCategory.BUSINESS))
            .addAction(0, "Personal", ClassifyReceiver.intent(context, trip.id, TripCategory.PERSONAL))
            .build()
        notify(context, tripNotificationId(trip.id), notification)
    }

    /** Used if Android refuses to start recording from the background. */
    fun promptToStart(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_TRIPS)
            .setSmallIcon(R.drawable.ic_stat_trip)
            .setColor(ContextCompat.getColor(context, R.color.mhe_orange))
            .setContentTitle("Car connected")
            .setContentText("Tap to start recording this trip")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, startTrip = true))
            .build()
        notify(context, ID_START_PROMPT, notification)
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    fun notify(context: Context, id: Int, notification: android.app.Notification) {
        val allowed = android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(context).notify(id, notification)
    }
}
