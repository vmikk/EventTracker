package dev.vmikk.eventtracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import dev.vmikk.eventtracker.databinding.ItemCalendarDayBinding
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class DayMarker(
    val colorArgb: Int? = null,
    val emoji: String? = null,
)

class MonthGridAdapter(
    private val onDateClick: (LocalDate) -> Unit,
    private val markersForDate: (LocalDate) -> List<DayMarker>,
) : RecyclerView.Adapter<MonthGridAdapter.DayViewHolder>() {

    private var cells: List<LocalDate?> = emptyList()

    fun submitMonth(month: YearMonth) {
        cells = buildCells(month)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val binding = ItemCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DayViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val date = cells[position]
        holder.bind(date)
    }

    override fun getItemCount(): Int = cells.size

    private fun buildCells(month: YearMonth): List<LocalDate?> {
        val first = month.atDay(1)
        val last = month.atEndOfMonth()

        val leadingBlanks = ((first.dayOfWeek.isoIndex() - DayOfWeek.MONDAY.isoIndex()) + 7) % 7
        val totalDays = last.dayOfMonth
        val totalCells = ((leadingBlanks + totalDays + 6) / 7) * 7

        return (0 until totalCells).map { idx ->
            val dayNumber = idx - leadingBlanks + 1
            if (dayNumber in 1..totalDays) month.atDay(dayNumber) else null
        }
    }

    inner class DayViewHolder(
        private val binding: ItemCalendarDayBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(date: LocalDate?) {
            binding.dayNumber.isVisible = date != null
            binding.markerContainer.isVisible = date != null
            binding.root.isClickable = date != null
            binding.root.isFocusable = date != null

            if (date == null) {
                binding.dayNumber.text = ""
                binding.markerContainer.removeAllViews()
                binding.root.setOnClickListener(null)
                return
            }

            binding.dayNumber.text = date.dayOfMonth.toString()
            binding.root.setOnClickListener { onDateClick(date) }

            val markers = markersForDate(date)
            renderMarkers(markers)
        }

        private fun renderMarkers(markers: List<DayMarker>) {
            binding.markerContainer.removeAllViews()
            if (markers.isEmpty()) return

            val emoji = markers.firstNotNullOfOrNull { it.emoji?.takeIf { e -> e.isNotBlank() } }
            if (emoji != null) {
                val tv = TextView(binding.root.context).apply {
                    text = emoji
                    textSize = 12f
                }
                binding.markerContainer.addView(tv)
                return
            }

            // Dots: show up to 3 for cleanliness
            val dotMarkers = markers.mapNotNull { it.colorArgb }.distinct().take(3)
            dotMarkers.forEach { color ->
                val dot = View(binding.root.context).apply {
                    setBackgroundResource(R.drawable.bg_calendar_dot)
                    background.setTint(color)
                }
                binding.markerContainer.addView(dot)
            }
        }
    }
}

private fun DayOfWeek.isoIndex(): Int = value // Monday=1 ... Sunday=7


