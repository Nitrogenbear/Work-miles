package uk.co.mheonsitetraining.miles.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface TripStatus {
    data object Idle : TripStatus

    /** The car is connected, but the phone isn't charging yet. */
    data object WaitingForCharge : TripStatus

    data class Recording(val tripId: Long, val startMillis: Long, val metres: Double) : TripStatus
}

/** Live status shared between the tracking service and the UI. */
object TripStatusStore {
    private val state = MutableStateFlow<TripStatus>(TripStatus.Idle)
    val status: StateFlow<TripStatus> = state.asStateFlow()

    internal fun set(status: TripStatus) {
        state.value = status
    }
}
