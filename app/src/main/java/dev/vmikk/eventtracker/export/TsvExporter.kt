package dev.vmikk.eventtracker.export

import android.content.Context
import dev.vmikk.eventtracker.data.AppDatabase
import dev.vmikk.eventtracker.data.EventRepository
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object TsvExporter {

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    suspend fun exportAllNonEmptyDates(context: Context, repo: EventRepository): File {
        val eventTypes = repo.getActiveEventTypesOnce()
        val db = AppDatabase.getInstance(context)
        val dayEvents = db.dayEventDao().getAllOnce()
        val customEvents = db.customEventDao().getAllOnce()

        val dayEventMap: Map<Long, Set<String>> =
            dayEvents.groupBy { it.dateEpochDay }
                .mapValues { (_, list) -> list.map { it.eventTypeId }.toSet() }

        val customMap: Map<Long, List<String>> =
            customEvents.groupBy { it.dateEpochDay }
                .mapValues { (_, list) -> list.map { it.text } }

        val allDates = (dayEventMap.keys + customMap.keys).toSortedSet()

        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outFile = File(exportsDir, "eventtracker-export-${System.currentTimeMillis()}.tsv")

        outFile.bufferedWriter(Charsets.UTF_8).use { w ->
            // Header
            val headers = buildList {
                add("Date")
                addAll(eventTypes.map { it.name })
                add("CustomEvents")
            }
            w.appendLine(headers.joinToString("\t") { escapeTsv(it) })

            allDates.forEach { epochDay ->
                val enabledIds = dayEventMap[epochDay].orEmpty()
                val customJoined = customMap[epochDay].orEmpty().joinToString(";")

                val row = buildList {
                    add(LocalDate.ofEpochDay(epochDay).format(dateFormatter))
                    addAll(eventTypes.map { type -> (enabledIds.contains(type.id)).toString() })
                    add(customJoined)
                }
                w.appendLine(row.joinToString("\t") { escapeTsv(it) })
            }
        }

        return outFile
    }

    private fun escapeTsv(value: String): String {
        val needsQuoting = value.any { it == '\t' || it == '\n' || it == '\r' || it == '"' }
        if (!needsQuoting) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}


