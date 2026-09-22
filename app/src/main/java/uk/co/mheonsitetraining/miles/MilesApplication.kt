package uk.co.mheonsitetraining.miles

import android.app.Application
import uk.co.mheonsitetraining.miles.tracking.Notifications

class MilesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
