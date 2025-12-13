package dev.vmikk.eventtracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EventTypeDao {
    @Query("SELECT * FROM event_types WHERE is_archived = 0 ORDER BY sort_order ASC, name ASC")
    fun observeActive(): Flow<List<EventTypeEntity>>

    @Query("SELECT * FROM event_types WHERE is_archived = 0 ORDER BY sort_order ASC, name ASC")
    suspend fun getActiveOnce(): List<EventTypeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(eventType: EventTypeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(eventTypes: List<EventTypeEntity>)

    @Update
    suspend fun update(eventType: EventTypeEntity)
}

@Dao
interface DayEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(dayEvent: DayEventEntity)

    @Delete
    suspend fun delete(dayEvent: DayEventEntity)

    @Query("SELECT * FROM day_events WHERE date_epoch_day = :dateEpochDay")
    suspend fun getByDateOnce(dateEpochDay: Long): List<DayEventEntity>

    @Query(
        "SELECT * FROM day_events WHERE date_epoch_day BETWEEN :startEpochDay AND :endEpochDay"
    )
    suspend fun getInRangeOnce(startEpochDay: Long, endEpochDay: Long): List<DayEventEntity>

    @Query("SELECT * FROM day_events")
    suspend fun getAllOnce(): List<DayEventEntity>
}

@Dao
interface CustomEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customEvent: CustomEventEntity)

    @Query("DELETE FROM custom_events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM custom_events WHERE date_epoch_day = :dateEpochDay ORDER BY created_at_epoch_ms ASC")
    suspend fun listByDateOnce(dateEpochDay: Long): List<CustomEventEntity>

    @Query("SELECT * FROM custom_events WHERE date_epoch_day BETWEEN :startEpochDay AND :endEpochDay")
    suspend fun listInRangeOnce(startEpochDay: Long, endEpochDay: Long): List<CustomEventEntity>

    @Query("SELECT * FROM custom_events")
    suspend fun getAllOnce(): List<CustomEventEntity>
}


