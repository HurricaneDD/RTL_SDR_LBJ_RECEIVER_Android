package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface LbjDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrainRecord(record: TrainRecord): Long

    @Query("SELECT * FROM train_records ORDER BY timestamp DESC LIMIT 200")
    fun getAllTrainRecords(): Flow<List<TrainRecord>>

    @Query("DELETE FROM train_records")
    suspend fun clearAllTrainRecords()

    @Query("DELETE FROM train_records WHERE id = :id")
    suspend fun deleteTrainRecord(id: Long)

    // Route station kilometers
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteStationKm(entity: RouteStationKmEntity)

    @Query("SELECT * FROM route_station_kms ORDER BY updatedTimestamp DESC")
    fun getAllRouteStationKms(): Flow<List<RouteStationKmEntity>>

    @Query("SELECT * FROM route_station_kms")
    suspend fun getAllRouteStationKmsList(): List<RouteStationKmEntity>

    @Query("DELETE FROM route_station_kms WHERE routeName = :routeName")
    suspend fun deleteRouteStationKm(routeName: String)
}

@Database(
    entities = [TrainRecord::class, RouteStationKmEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LbjDatabase : RoomDatabase() {
    abstract fun lbjDao(): LbjDao

    companion object {
        @Volatile
        private var INSTANCE: LbjDatabase? = null

        fun getDatabase(context: Context): LbjDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LbjDatabase::class.java,
                    "lbj_receiver_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
