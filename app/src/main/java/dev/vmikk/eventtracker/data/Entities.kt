package dev.vmikk.eventtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "event_types",
    indices = [
        Index(value = ["name"], unique = false),
        Index(value = ["sort_order"], unique = false),
    ],
)
data class EventTypeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "color_argb")
    val colorArgb: Int,

    @ColumnInfo(name = "emoji")
    val emoji: String?,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean,
)

@Entity(
    tableName = "day_events",
    primaryKeys = ["date_epoch_day", "event_type_id"],
    indices = [
        Index(value = ["date_epoch_day"], unique = false),
        Index(value = ["event_type_id"], unique = false),
    ],
)
data class DayEventEntity(
    @ColumnInfo(name = "date_epoch_day")
    val dateEpochDay: Long,

    @ColumnInfo(name = "event_type_id")
    val eventTypeId: String,
)

@Entity(
    tableName = "custom_events",
    indices = [
        Index(value = ["date_epoch_day"], unique = false),
    ],
)
data class CustomEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "date_epoch_day")
    val dateEpochDay: Long,

    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
)



