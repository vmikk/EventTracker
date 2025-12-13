package dev.vmikk.eventtracker.data

import android.content.Context
import dev.vmikk.eventtracker.DayCellData
import dev.vmikk.eventtracker.DayMarker
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class EventRepository(
    private val db: AppDatabase
) {
    fun observeActiveEventTypes() = db.eventTypeDao().observeActive()

    suspend fun getActiveEventTypesOnce(): List<EventTypeEntity> = db.eventTypeDao().getActiveOnce()

    suspend fun upsertEventType(eventType: EventTypeEntity) {
        db.eventTypeDao().upsert(eventType)
    }

    suspend fun createEventType(
        name: String,
        colorArgb: Int,
        emoji: String?,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return

        val existing = db.eventTypeDao().getActiveOnce()
        val nextSort = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1

        db.eventTypeDao().upsert(
            EventTypeEntity(
                id = UUID.randomUUID().toString(),
                name = trimmedName,
                colorArgb = colorArgb,
                emoji = emoji?.trim()?.takeIf { it.isNotBlank() },
                sortOrder = nextSort,
                isArchived = false,
            )
        )
    }

    suspend fun updateEventType(
        id: String,
        name: String,
        colorArgb: Int,
        emoji: String?,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return

        val existing = db.eventTypeDao().getActiveOnce().firstOrNull { it.id == id } ?: return
        db.eventTypeDao().upsert(
            existing.copy(
                name = trimmedName,
                colorArgb = colorArgb,
                emoji = emoji?.trim()?.takeIf { it.isNotBlank() },
            )
        )
    }

    suspend fun ensureDefaultEventTypesIfEmpty() {
        val existing = db.eventTypeDao().getActiveOnce()
        if (existing.isNotEmpty()) return

        // Lightweight defaults; user can rename/delete later.
        val defaults = listOf(
            EventTypeEntity(
                id = UUID.randomUUID().toString(),
                name = "Workout",
                colorArgb = 0xFF1E88E5.toInt(),
                emoji = "🏋️",
                sortOrder = 0,
                isArchived = false,
            ),
            EventTypeEntity(
                id = UUID.randomUUID().toString(),
                name = "Study",
                colorArgb = 0xFF43A047.toInt(),
                emoji = "📚",
                sortOrder = 1,
                isArchived = false,
            ),
            EventTypeEntity(
                id = UUID.randomUUID().toString(),
                name = "Social",
                colorArgb = 0xFFF4511E.toInt(),
                emoji = "🎉",
                sortOrder = 2,
                isArchived = false,
            ),
        )
        db.eventTypeDao().upsertAll(defaults)
    }

    suspend fun toggleEvent(date: LocalDate, eventTypeId: String, enabled: Boolean) {
        val entity = DayEventEntity(dateEpochDay = date.toEpochDay(), eventTypeId = eventTypeId)
        if (enabled) db.dayEventDao().insert(entity) else db.dayEventDao().delete(entity)
    }

    suspend fun getEnabledEventTypeIdsOnce(date: LocalDate): Set<String> =
        db.dayEventDao().getByDateOnce(date.toEpochDay()).map { it.eventTypeId }.toSet()

    suspend fun addCustomEvent(date: LocalDate, text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        db.customEventDao().insert(
            CustomEventEntity(
                id = UUID.randomUUID().toString(),
                dateEpochDay = date.toEpochDay(),
                text = trimmed,
                createdAtEpochMs = System.currentTimeMillis(),
            )
        )
    }

    suspend fun deleteCustomEvent(id: String) {
        db.customEventDao().deleteById(id)
    }

    suspend fun listCustomEventsOnce(date: LocalDate): List<CustomEventEntity> =
        db.customEventDao().listByDateOnce(date.toEpochDay())

    suspend fun getMonthMarkers(month: YearMonth): Map<LocalDate, DayCellData> {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()

        val eventTypes = db.eventTypeDao().getActiveOnce().associateBy { it.id }
        val dayEvents = db.dayEventDao().getInRangeOnce(start.toEpochDay(), end.toEpochDay())
        val customEvents = db.customEventDao().listInRangeOnce(start.toEpochDay(), end.toEpochDay())

        val dayEventsByDate = dayEvents.groupBy { LocalDate.ofEpochDay(it.dateEpochDay) }
        val customCountByDate = customEvents.groupingBy { LocalDate.ofEpochDay(it.dateEpochDay) }.eachCount()

        val allDates = (dayEventsByDate.keys + customCountByDate.keys).toSet()
        return allDates.associateWith { date ->
            val eventTypeMarkers: List<DayMarker> = dayEventsByDate[date].orEmpty().mapNotNull { ev ->
                val type = eventTypes[ev.eventTypeId] ?: return@mapNotNull null
                DayMarker(
                    colorArgb = type.colorArgb,
                    emoji = type.emoji,
                )
            }
            DayCellData(
                eventTypeMarkers = eventTypeMarkers,
                customEventCount = customCountByDate[date] ?: 0,
            )
        }
    }

    companion object {
        fun from(context: Context): EventRepository =
            EventRepository(AppDatabase.getInstance(context))
    }
}


