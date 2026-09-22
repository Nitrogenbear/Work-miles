package uk.co.mheonsitetraining.miles.tracking

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uk.co.mheonsitetraining.miles.core.TripCategory
import uk.co.mheonsitetraining.miles.data.MilesDatabase

/** Handles the "Work" / "Personal" buttons on the end-of-trip notification. */
class ClassifyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1)
        val category = intent.getStringExtra(EXTRA_CATEGORY)
            ?.let { name -> TripCategory.entries.firstOrNull { it.name == name } }
        if (tripId < 0 || category == null) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MilesDatabase.get(context).trips().setCategory(tripId, category)
                Notifications.cancel(context, Notifications.tripNotificationId(tripId))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_TRIP_ID = "tripId"
        private const val EXTRA_CATEGORY = "category"

        fun intent(context: Context, tripId: Long, category: TripCategory): PendingIntent {
            val intent = Intent(context, ClassifyReceiver::class.java)
                .putExtra(EXTRA_TRIP_ID, tripId)
                .putExtra(EXTRA_CATEGORY, category.name)
            return PendingIntent.getBroadcast(
                context,
                Notifications.tripNotificationId(tripId) * 4 + category.ordinal,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
