package dev.vmikk.eventtracker

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
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
    val isNegated: Boolean = false,
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
        val oldSize = cells.size
        cells = buildCells(month)
        val newSize = cells.size

        when {
            newSize == oldSize -> {
                // Same size: use range change notification
                notifyItemRangeChanged(0, newSize)
            }
            newSize > oldSize -> {
                // Size increased: notify existing items changed, then insert new ones
                if (oldSize > 0) {
                    notifyItemRangeChanged(0, oldSize)
                }
                notifyItemRangeInserted(oldSize, newSize - oldSize)
            }
            else -> {
                // Size decreased: notify existing items changed, then remove old ones
                if (newSize > 0) {
                    notifyItemRangeChanged(0, newSize)
                }
                notifyItemRangeRemoved(newSize, oldSize - newSize)
            }
        }
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

    /**
     * Notifies that markers/content have changed for date cells only.
     * More efficient than notifyDataSetChanged() as it skips empty cells.
     */
    fun notifyDateCellsChanged() {
        cells.forEachIndexed { index, date ->
            if (date != null) {
                notifyItemChanged(index)
            }
        }
    }

    /**
     * Notifies that all cells need to be updated (e.g., when layout properties change).
     * More efficient than notifyDataSetChanged() as it uses a range notification.
     */
    fun notifyAllCellsChanged() {
        if (cells.isNotEmpty()) {
            notifyItemRangeChanged(0, cells.size)
        }
    }

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
            // - Custom events: show a single marker if there are custom events and space.
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
                            if (m.isNegated) {
                                // Desaturate emoji for negated events
                                alpha = 0.5f
                                val colorMatrix = ColorMatrix().apply {
                                    setSaturation(0f) // Fully desaturate (grayscale)
                                }
                                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                            }
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
                                if (m.isNegated) {
                                    // Desaturate dot for negated events
                                    alpha = 0.5f
                                    val colorMatrix = ColorMatrix().apply {
                                        setSaturation(0f) // Fully desaturate (grayscale)
                                    }
                                    background.colorFilter = ColorMatrixColorFilter(colorMatrix)
                                    background.setTint(color) // Still use original color, but desaturated
                                } else {
                                    background.setTint(color)
                                }
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

            // Show marker for custom events if there are any and space
            val remaining = (maxIcons - shownIcons).coerceAtLeast(0)
            if (customCount > 0 && remaining > 0) {
                val size = dpToPx(14f)
                val margin = dpToPx(2f)
                val customEventMarker = View(binding.root.context).apply {
                    setBackgroundResource(R.drawable.bg_calendar_custom_event)
                    background.setTint(ContextCompat.getColor(binding.root.context, R.color.light_blue))
                }
                customEventMarker.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = margin
                }
                binding.markerContainer.addView(customEventMarker)
                shownIcons++
            }

            val showBadge = totalCount > shownIcons
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




