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

    suspend fun isEventTypeInUse(eventTypeId: String): Boolean =
        db.eventTypeDao().countUsage(eventTypeId) > 0

    suspend fun deleteEventType(eventTypeId: String) {
        // First delete all associated day events
        db.dayEventDao().deleteByEventTypeId(eventTypeId)
        // Then delete the event type itself
        db.eventTypeDao().deleteById(eventTypeId)
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

    suspend fun setEventState(date: LocalDate, eventTypeId: String, state: Int?) {
        if (state == null) {
            val entity = DayEventEntity(
                dateEpochDay = date.toEpochDay(),
                eventTypeId = eventTypeId,
                state = DayEventEntity.STATE_HAPPENED
            )
            db.dayEventDao().delete(entity)
        } else {
            val entity = DayEventEntity(
                dateEpochDay = date.toEpochDay(),
                eventTypeId = eventTypeId,
                state = state
            )
            db.dayEventDao().insert(entity)
        }
    }

    suspend fun getEventTypeStatesOnce(date: LocalDate): Map<String, Int> =
        db.dayEventDao().getByDateOnce(date.toEpochDay())
            .associate { it.eventTypeId to it.state }

    suspend fun addCustomEvent(date: LocalDate, text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return false

        // Check if a custom event with the same text already exists for this date
        val existingEvents = db.customEventDao().listByDateOnce(date.toEpochDay())
        if (existingEvents.any { it.text.trim().equals(trimmed, ignoreCase = true) }) {
            return false // Duplicate found, don't add
        }

        db.customEventDao().insert(
            CustomEventEntity(
                id = UUID.randomUUID().toString(),
                dateEpochDay = date.toEpochDay(),
                text = trimmed,
                createdAtEpochMs = System.currentTimeMillis(),
            )
        )
        return true
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
                    isNegated = ev.state == DayEventEntity.STATE_NEGATED,
                )
            }
            DayCellData(
                eventTypeMarkers = eventTypeMarkers,
                customEventCount = customCountByDate[date] ?: 0,
            )
        }
    }

    data class EventTypeCountWithNegated(
        val eventTypeId: String,
        val name: String,
        val happenedCount: Int,
        val negatedCount: Int,
    )

    data class SummaryCounts(
        val eventTypeCounts: List<EventTypeCountWithNegated>,
        val customTextCounts: List<Pair<String, Int>>,
    )

    suspend fun getSummaryCounts(start: LocalDate, end: LocalDate): SummaryCounts {
        val startEpoch = start.toEpochDay()
        val endEpoch = end.toEpochDay()

        val happenedCounts = db.dayEventDao().countByEventTypeInRangeOnce(startEpoch, endEpoch)
        val negatedCounts = db.dayEventDao().countNegatedByEventTypeInRangeOnce(startEpoch, endEpoch)
        val rawTextCounts = db.customEventDao().countByTextInRangeOnce(startEpoch, endEpoch)
        val eventTypes = db.eventTypeDao().getActiveOnce().associateBy { it.id }

        // Combine happened and negated counts by event type
        val happenedMap = happenedCounts.associateBy { it.eventTypeId }
        val negatedMap = negatedCounts.associateBy { it.eventTypeId }
        val allEventTypeIds = (happenedMap.keys + negatedMap.keys).toSet()

        val eventTypeCounts = allEventTypeIds.map { eventTypeId ->
            val happened = happenedMap[eventTypeId]
            val negated = negatedMap[eventTypeId]
            EventTypeCountWithNegated(
                eventTypeId = eventTypeId,
                name = happened?.name ?: negated?.name ?: "",
                happenedCount = happened?.cnt ?: 0,
                negatedCount = negated?.cnt ?: 0,
            )
        }.sortedWith(
            compareBy<EventTypeCountWithNegated> { type ->
                eventTypes[type.eventTypeId]?.sortOrder ?: Int.MAX_VALUE
            }.thenBy { it.name }
        )

        // Normalize by trim + case-insensitive.
        val normalized = LinkedHashMap<String, Pair<String, Int>>() // key -> (displayText, count)
        for (row in rawTextCounts) {
            val trimmed = row.text.trim()
            if (trimmed.isBlank()) continue
            val key = trimmed.lowercase()
            val existing = normalized[key]
            if (existing == null) {
                normalized[key] = trimmed to row.cnt
            } else {
                normalized[key] = existing.first to (existing.second + row.cnt)
            }
        }

        val customTextCounts = normalized.values
            .map { it.first to it.second }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

        return SummaryCounts(
            eventTypeCounts = eventTypeCounts,
            customTextCounts = customTextCounts,
        )
    }

    companion object {
        fun from(context: Context): EventRepository =
            EventRepository(AppDatabase.getInstance(context))
    }
}


