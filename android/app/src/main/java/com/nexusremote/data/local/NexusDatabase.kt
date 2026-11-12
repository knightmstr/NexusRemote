package com.nexusremote.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import com.nexusremote.domain.model.DeviceCapability
import com.nexusremote.domain.model.DeviceOnlineStatus
import com.nexusremote.domain.model.ProtocolType
import com.nexusremote.domain.model.SavedDevice
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {

    @Query("SELECT * FROM saved_devices ORDER BY isDefault DESC, isFavourite DESC, lastConnectedTimestamp DESC")
    fun getAllDevicesFlow(): Flow<List<SavedDevice>>

    @Query("SELECT * FROM saved_devices WHERE isFavourite = 1 ORDER BY lastConnectedTimestamp DESC")
    fun getFavouriteDevicesFlow(): Flow<List<SavedDevice>>

    @Query("SELECT * FROM saved_devices WHERE id = :id LIMIT 1")
    suspend fun getDeviceById(id: String): SavedDevice?

    @Query("SELECT * FROM saved_devices WHERE hostIp = :ip LIMIT 1")
    suspend fun getDeviceByIp(ip: String): SavedDevice?

    @Query("SELECT * FROM saved_devices WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultDevice(): SavedDevice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: SavedDevice)

    @Update
    suspend fun updateDevice(device: SavedDevice)

    @Delete
    suspend fun deleteDevice(device: SavedDevice)

    @Query("DELETE FROM saved_devices WHERE id = :id")
    suspend fun deleteDeviceById(id: String)

    @Query("UPDATE saved_devices SET isDefault = 0")
    suspend fun clearDefaultDevices()

    @Query("UPDATE saved_devices SET isDefault = 1 WHERE id = :id")
    suspend fun setDefaultDevice(id: String)

    @Query("UPDATE saved_devices SET lastConnectedTimestamp = :timestamp, hostIp = :currentIp WHERE id = :id")
    suspend fun updateLastConnected(id: String, timestamp: Long, currentIp: String)

    @Query("UPDATE saved_devices SET onlineStatus = :status WHERE id = :id")
    suspend fun updateOnlineStatus(id: String, status: DeviceOnlineStatus)

    @Query("UPDATE saved_devices SET name = :newName, room = :newRoom WHERE id = :id")
    suspend fun renameDevice(id: String, newName: String, newRoom: String)

    @Query("UPDATE saved_devices SET isFavourite = :isFav WHERE id = :id")
    suspend fun toggleFavourite(id: String, isFav: Boolean)
}

class Converters {
    @TypeConverter
    fun fromProtocolType(value: ProtocolType): String = value.name

    @TypeConverter
    fun toProtocolType(value: String): ProtocolType = ProtocolType.valueOf(value)

    @TypeConverter
    fun fromOnlineStatus(value: DeviceOnlineStatus): String = value.name

    @TypeConverter
    fun toOnlineStatus(value: String): DeviceOnlineStatus = DeviceOnlineStatus.valueOf(value)

    @TypeConverter
    fun fromCapabilitySet(set: Set<DeviceCapability>): String {
        return set.joinToString(",") { it.name }
    }

    @TypeConverter
    fun toCapabilitySet(value: String): Set<DeviceCapability> {
        if (value.isBlank()) return emptySet()
        return value.split(",").mapNotNull {
            try { DeviceCapability.valueOf(it.trim()) } catch (_: Exception) { null }
        }.toSet()
    }
}

@Database(entities = [SavedDevice::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao

    companion object {
        @Volatile
        private var INSTANCE: NexusDatabase? = null

        fun getInstance(context: Context): NexusDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NexusDatabase::class.java,
                    "nexus_remote_database.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
