package dev.vmikk.eventtracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.ColumnInfo
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

    @Query("SELECT COUNT(*) FROM day_events WHERE event_type_id = :eventTypeId")
    suspend fun countUsage(eventTypeId: String): Int

    @Query("DELETE FROM event_types WHERE id = :eventTypeId")
    suspend fun deleteById(eventTypeId: String)
}

@Dao
interface DayEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dayEvent: DayEventEntity)

    @Delete
    suspend fun delete(dayEvent: DayEventEntity)

    @Query("SELECT * FROM day_events WHERE date_epoch_day = :dateEpochDay")
    suspend fun getByDateOnce(dateEpochDay: Long): List<DayEventEntity>

    @Query(
        "SELECT * FROM day_events WHERE date_epoch_day BETWEEN :startEpochDay AND :endEpochDay"
    )
    suspend fun getInRangeOnce(startEpochDay: Long, endEpochDay: Long): List<DayEventEntity>

    data class EventTypeCountRow(
        @ColumnInfo(name = "event_type_id")
        val eventTypeId: String,
        @ColumnInfo(name = "name")
        val name: String,
        @ColumnInfo(name = "cnt")
        val cnt: Int,
    )

    @Query(
        """
        SELECT de.event_type_id AS event_type_id, et.name AS name, COUNT(*) AS cnt
        FROM day_events de
        JOIN event_types et ON et.id = de.event_type_id
        WHERE de.date_epoch_day BETWEEN :startEpochDay AND :endEpochDay
        AND de.state = 1
        GROUP BY de.event_type_id, et.name
        ORDER BY et.sort_order ASC, et.name ASC
        """
    )
    suspend fun countByEventTypeInRangeOnce(startEpochDay: Long, endEpochDay: Long): List<EventTypeCountRow>

    @Query(
        """
        SELECT de.event_type_id AS event_type_id, et.name AS name, COUNT(*) AS cnt
        FROM day_events de
        JOIN event_types et ON et.id = de.event_type_id
        WHERE de.date_epoch_day BETWEEN :startEpochDay AND :endEpochDay
        AND de.state = 2
        GROUP BY de.event_type_id, et.name
        ORDER BY et.sort_order ASC, et.name ASC
        """
    )
    suspend fun countNegatedByEventTypeInRangeOnce(startEpochDay: Long, endEpochDay: Long): List<EventTypeCountRow>

    @Query("SELECT * FROM day_events")
    suspend fun getAllOnce(): List<DayEventEntity>

    @Query("DELETE FROM day_events WHERE event_type_id = :eventTypeId")
    suspend fun deleteByEventTypeId(eventTypeId: String)
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

    data class CustomTextCountRow(
        @ColumnInfo(name = "text")
        val text: String,
        @ColumnInfo(name = "cnt")
        val cnt: Int,
    )

    @Query(
        """
        SELECT text AS text, COUNT(*) AS cnt
        FROM custom_events
        WHERE date_epoch_day BETWEEN :startEpochDay AND :endEpochDay
        GROUP BY text
        """
    )
    suspend fun countByTextInRangeOnce(startEpochDay: Long, endEpochDay: Long): List<CustomTextCountRow>

    @Query("SELECT * FROM custom_events")
    suspend fun getAllOnce(): List<CustomEventEntity>
}


