package uk.co.mheonsitetraining.miles.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import uk.co.mheonsitetraining.miles.core.TripCategory
import uk.co.mheonsitetraining.miles.core.TripRecord
import uk.co.mheonsitetraining.miles.core.metresToMiles

@Entity(tableName = "trips", indices = [Index("startMillis"), Index("endMillis")])
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMillis: Long,
    /** Null while the trip is still being recorded. */
    val endMillis: Long? = null,
    val distanceMetres: Double = 0.0,
    val category: TripCategory = TripCategory.UNCLASSIFIED,
    val purpose: String = "",
    val fromAddress: String = "",
    val toAddress: String = "",
    val startLat: Double? = null,
    val startLng: Double? = null,
    val endLat: Double? = null,
    val endLng: Double? = null,
    /** True when the trip was typed in by hand rather than recorded by GPS. */
    val manual: Boolean = false,
) {
    val miles: Double get() = metresToMiles(distanceMetres)

    val inProgress: Boolean get() = endMillis == null

    fun toRecord() = TripRecord(
        id = id,
        startMillis = startMillis,
        endMillis = endMillis ?: startMillis,
        miles = miles,
        category = category,
        fromAddress = fromAddress,
        toAddress = toAddress,
        purpose = purpose,
    )
}
