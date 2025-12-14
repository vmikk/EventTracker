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
import android.widget.LinearLayout

data class DayMarker(
    val colorArgb: Int? = null,
    val emoji: String? = null,
)

data class DayCellData(
    val eventTypeMarkers: List<DayMarker> = emptyList(),
    val customEventCount: Int = 0,
)

class MonthGridAdapter(
    private val onDateClick: (LocalDate) -> Unit,
    private val dayCellForDate: (LocalDate) -> DayCellData,
) : RecyclerView.Adapter<MonthGridAdapter.DayViewHolder>() {

    private var cells: List<LocalDate?> = emptyList()

    var maxMarkersPerDay: Int = 3
    var cellHeightPx: Int? = null

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
            binding.countBadge.isVisible = false
            binding.root.isClickable = date != null
            binding.root.isFocusable = date != null

            if (date == null) {
                binding.dayNumber.text = ""
                binding.markerContainer.removeAllViews()
                binding.root.setOnClickListener(null)
                return
            }

            // Allow CalendarFragment to control cell height on tall screens.
            val desiredHeight = cellHeightPx
            if (desiredHeight != null) {
                val lp = binding.root.layoutParams
                if (lp != null && lp.height != desiredHeight) {
                    lp.height = desiredHeight
                    binding.root.layoutParams = lp
                }
            }

            binding.dayNumber.text = date.dayOfMonth.toString()
            binding.root.setOnClickListener { onDateClick(date) }

            val cell = dayCellForDate(date)
            renderCell(cell)
        }

        private fun renderCell(cell: DayCellData) {
            binding.markerContainer.removeAllViews()
            val eventTypeMarkers = cell.eventTypeMarkers
            val customCount = cell.customEventCount
            val totalCount = eventTypeMarkers.size + customCount
            if (totalCount <= 0) return

            // Icons shown inside the cell: up to maxMarkersPerDay.
            // - Event types: show emoji if present, otherwise a colored dot.
            // - Custom events: if there are any event-type markers and there is remaining
            //   space, show a neutral marker per custom entry up to remaining capacity.
            // - If the day has ONLY custom events, we intentionally show NO icons here
            //   (only the count badge).
            val maxIcons = maxMarkersPerDay.coerceAtLeast(0)

            var shownIcons = 0
            if (eventTypeMarkers.isNotEmpty() && maxIcons > 0) {
                for (m in eventTypeMarkers) {
                    if (shownIcons >= maxIcons) break
                    val emoji = m.emoji?.takeIf { it.isNotBlank() }
                    if (emoji != null) {
                        val tv = TextView(binding.root.context).apply {
                            text = emoji
                            textSize = 12f
                        }
                        binding.markerContainer.addView(tv)
                        shownIcons++
                    } else {
                        val color = m.colorArgb
                        if (color != null) {
                            val size = dpToPx(14f)
                            val margin = dpToPx(2f)
                            val dot = View(binding.root.context).apply {
                                setBackgroundResource(R.drawable.bg_calendar_dot)
                                background.setTint(color)
                            }
                            dot.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                                marginEnd = margin
                            }
                            binding.markerContainer.addView(dot)
                            shownIcons++
                        }
                    }
                }
            }

            val remaining = (maxIcons - shownIcons).coerceAtLeast(0)
            if (eventTypeMarkers.isNotEmpty() && customCount > 0 && remaining > 0) {
                repeat(minOf(customCount, remaining)) {
                    val tv = TextView(binding.root.context).apply {
                        text = "✎"
                        textSize = 10f
                    }
                    tv.layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = dpToPx(2f)
                    }
                    binding.markerContainer.addView(tv)
                    shownIcons++
                }
            }

            val showBadge =
                (eventTypeMarkers.isEmpty() && customCount > 0) || (totalCount > shownIcons)
            if (showBadge) {
                binding.countBadge.text = totalCount.toString()
                binding.countBadge.isVisible = true
            } else {
                binding.countBadge.isVisible = false
            }
        }

        private fun dpToPx(dp: Float): Int {
            return (dp * binding.root.resources.displayMetrics.density).toInt()
        }
    }
}

private fun DayOfWeek.isoIndex(): Int = value // Monday=1 ... Sunday=7




