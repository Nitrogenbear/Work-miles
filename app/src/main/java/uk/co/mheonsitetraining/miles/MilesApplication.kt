package uk.co.mheonsitetraining.miles

import android.app.Application
import uk.co.mheonsitetraining.miles.tracking.EventLog
import uk.co.mheonsitetraining.miles.tracking.Notifications

class MilesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EventLog.init(this)
        Notifications.createChannels(this)

        // Note crashes in the activity log so they show up in Setup.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                EventLog.log(this, "App crashed: ${error.javaClass.simpleName}: ${error.message} at ${error.stackTrace.firstOrNull()}")
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
