package com.example.segundoparcialmovil2026_2.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * The one table this project needs: a captured location point.
 *
 * - Stored locally in SQLite (Room) immediately, so it works with no connection.
 * - [synced] is the offline/online flag: 0 = still only on the phone, 1 = already in Firestore.
 */
@Entity(tableName = "location_points")
data class LocationPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    /** Epoch millis, captured when the fix was received. */
    val capturedAt: Long,
    val accuracyMeters: Float,
    val synced: Boolean = false,
)

@Dao
interface LocationPointDao {

    @Query("SELECT * FROM location_points ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<LocationPoint>>

    @Query("SELECT * FROM location_points WHERE synced = 0 ORDER BY capturedAt ASC")
    suspend fun getPendingSync(): List<LocationPoint>

    @Insert
    suspend fun insert(point: LocationPoint): Long

    @Query("UPDATE location_points SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("SELECT COUNT(*) FROM location_points")
    suspend fun count(): Int
}

@Database(
    entities = [LocationPoint::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun locationPointDao(): LocationPointDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: android.content.Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder<AppDatabase>(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "segundo_parcial.db",
                ).build().also { instance = it }
            }
    }
}
