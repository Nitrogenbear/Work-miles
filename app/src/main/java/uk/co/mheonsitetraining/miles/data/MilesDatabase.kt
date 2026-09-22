package uk.co.mheonsitetraining.miles.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import uk.co.mheonsitetraining.miles.core.TripCategory

class Converters {
    @TypeConverter
    fun fromCategory(category: TripCategory): String = category.name

    @TypeConverter
    fun toCategory(value: String): TripCategory =
        TripCategory.entries.firstOrNull { it.name == value } ?: TripCategory.UNCLASSIFIED
}

@Database(entities = [TripEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class MilesDatabase : RoomDatabase() {
    abstract fun trips(): TripDao

    companion object {
        @Volatile
        private var instance: MilesDatabase? = null

        fun get(context: Context): MilesDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MilesDatabase::class.java,
                    "miles.db",
                ).build().also { instance = it }
            }
    }
}
