package dev.vmikk.eventtracker

import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.vmikk.eventtracker.data.EventRepository
import dev.vmikk.eventtracker.databinding.FragmentSummaryBinding
import dev.vmikk.eventtracker.databinding.ItemSummaryHeaderBinding
import dev.vmikk.eventtracker.databinding.ItemSummaryRowBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

class SummaryFragment : Fragment() {

    private var _binding: FragmentSummaryBinding? = null
    private val binding get() = _binding!!

    private val repo by lazy { EventRepository.from(requireContext()) }

    private enum class Mode { WEEK, MONTH, YEAR }

    private var mode: Mode = Mode.MONTH
    private var anchorDate: LocalDate = LocalDate.now()

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_ANCHOR_DATE_EPOCH_DAY = "anchor_date_epoch_day"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Restore state if available
        savedInstanceState?.let {
            val modeOrdinal = it.getInt(KEY_MODE, Mode.MONTH.ordinal)
            mode = Mode.values().getOrElse(modeOrdinal) { Mode.MONTH }
            val epochDay = it.getLong(KEY_ANCHOR_DATE_EPOCH_DAY, -1)
            if (epochDay >= 0) {
                anchorDate = LocalDate.ofEpochDay(epochDay)
            }
        }

        binding.summaryList.layoutManager = LinearLayoutManager(requireContext())
        val adapter = SummaryAdapter()
        binding.summaryList.adapter = adapter

        // Listen for day events changes from other fragments
        parentFragmentManager.setFragmentResultListener(
            DayEventsBottomSheet.REQUEST_KEY_DAY_EVENTS_CHANGED,
            viewLifecycleOwner
        ) { _, _ ->
            refresh(adapter)
        }

        // Restore period toggle selection
        val toggleId = when (mode) {
            Mode.WEEK -> R.id.toggle_week
            Mode.YEAR -> R.id.toggle_year
            Mode.MONTH -> R.id.toggle_month
        }
        binding.periodToggle.check(toggleId)

        binding.periodToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            mode = when (checkedId) {
                R.id.toggle_week -> Mode.WEEK
                R.id.toggle_year -> Mode.YEAR
                else -> Mode.MONTH
            }
            refresh(adapter)
        }

        binding.prevPeriod.setOnClickListener {
            anchorDate = when (mode) {
                Mode.WEEK -> anchorDate.minusWeeks(1)
                Mode.MONTH -> anchorDate.minusMonths(1)
                Mode.YEAR -> anchorDate.minusYears(1)
            }
            refresh(adapter)
        }

        binding.nextPeriod.setOnClickListener {
            anchorDate = when (mode) {
                Mode.WEEK -> anchorDate.plusWeeks(1)
                Mode.MONTH -> anchorDate.plusMonths(1)
                Mode.YEAR -> anchorDate.plusYears(1)
            }
            refresh(adapter)
        }

        refresh(adapter)
    }

    private fun refresh(adapter: SummaryAdapter) {
        val (start, end, title) = computeRangeAndTitle(anchorDate, mode)
        binding.periodTitle.text = title

        lifecycleScope.launch {
            binding.progress.isVisible = true
            try {
                val counts = withContext(Dispatchers.IO) { repo.getSummaryCounts(start, end) }
                val rows = buildRows(counts)
                adapter.submit(rows)
                // Show empty state if no data (only headers or empty)
                val isEmpty = rows.isEmpty() || (rows.size == 2 && rows.all { it is SummaryRow.Header })
                binding.emptyState.isVisible = isEmpty
                binding.summaryList.isVisible = !isEmpty
            } catch (e: Exception) {
                android.util.Log.e("SummaryFragment", "Error loading summary", e)
                com.google.android.material.snackbar.Snackbar.make(
                    binding.root,
                    getString(R.string.error_loading_events),
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                ).show()
            } finally {
                binding.progress.isVisible = false
            }
        }
    }

    private fun buildRows(counts: EventRepository.SummaryCounts): List<SummaryRow> {
        val rows = mutableListOf<SummaryRow>()

        rows += SummaryRow.Header(getString(R.string.summary_event_types))
        counts.eventTypeCounts.forEach { r ->
            rows += SummaryRow.Item(
                label = r.name,
                happenedCount = r.happenedCount,
                negatedCount = r.negatedCount
            )
        }

        rows += SummaryRow.Header(getString(R.string.summary_custom_texts))
        counts.customTextCounts.forEach { (text, cnt) ->
            rows += SummaryRow.Item(label = text, happenedCount = cnt, negatedCount = 0)
        }

        return rows
    }

    private data class RangeTitle(val start: LocalDate, val end: LocalDate, val title: String)

    private fun computeRangeAndTitle(anchor: LocalDate, mode: Mode): RangeTitle {
        return when (mode) {
            Mode.MONTH -> {
                val ym = YearMonth.from(anchor)
                val start = ym.atDay(1)
                val end = ym.atEndOfMonth()
                val fmt = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())
                RangeTitle(start, end, ym.atDay(1).format(fmt))
            }
            Mode.YEAR -> {
                val year = anchor.year
                val start = LocalDate.of(year, 1, 1)
                val end = LocalDate.of(year, 12, 31)
                RangeTitle(start, end, year.toString())
            }
            Mode.WEEK -> {
                val start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val end = start.plusDays(6)
                val dateFormatter = DateTimeFormatter.ofPattern("MMM dd", Locale.getDefault())
                val year = start.year
                val title = if (start.month == end.month) {
                    // Same month: "Nov 01 - 07, 2025"
                    val monthDay = start.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))
                    val startDay = start.dayOfMonth.toString().padStart(2, '0')
                    val endDay = end.dayOfMonth.toString().padStart(2, '0')
                    "$monthDay $startDay - $endDay, $year"
                } else {
                    // Different months: "Nov 27 - Dec 02, 2025" or "Dec 29, 2024 - Jan 04, 2025" if spans years
                    if (start.year == end.year) {
                        "${start.format(dateFormatter)} - ${end.format(dateFormatter)}, $year"
                    } else {
                        val dateFormatterWithYear = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())
                        "${start.format(dateFormatterWithYear)} - ${end.format(dateFormatterWithYear)}"
                    }
                }
                RangeTitle(start, end, title)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_MODE, mode.ordinal)
        outState.putLong(KEY_ANCHOR_DATE_EPOCH_DAY, anchorDate.toEpochDay())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private sealed class SummaryRow {
        data class Header(val title: String) : SummaryRow()
        data class Item(val label: String, val happenedCount: Int, val negatedCount: Int) : SummaryRow()
    }

    private class SummaryAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val items = mutableListOf<SummaryRow>()

        fun submit(rows: List<SummaryRow>) {
            items.clear()
            items.addAll(rows)
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is SummaryRow.Header -> 0
                is SummaryRow.Item -> 1
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                0 -> HeaderVH(ItemSummaryHeaderBinding.inflate(inflater, parent, false))
                else -> ItemVH(ItemSummaryRowBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = items[position]) {
                is SummaryRow.Header -> (holder as HeaderVH).bind(row)
                is SummaryRow.Item -> (holder as ItemVH).bind(row)
            }
        }

        override fun getItemCount(): Int = items.size

        private class HeaderVH(private val binding: ItemSummaryHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(row: SummaryRow.Header) {
                binding.headerTitle.text = row.title
            }
        }

        private class ItemVH(private val binding: ItemSummaryRowBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(row: SummaryRow.Item) {
                binding.label.text = row.label
                if (row.negatedCount > 0) {
                    // Format: "4 / 1" where 4 is happened, 1 is negated (in red)
                    val text = "${row.happenedCount} / ${row.negatedCount}"
                    binding.count.text = text
                    // Set color spans: happened count in default color, negated count in red
                    val spannable = SpannableString(text)
                    val redColor = ContextCompat.getColor(binding.root.context, android.R.color.holo_red_dark)
                    val negatedStart = text.indexOf("/") + 2 // Start after " / "
                    val negatedEnd = text.length
                    if (negatedStart < text.length) {
                        spannable.setSpan(
                            ForegroundColorSpan(redColor),
                            negatedStart,
                            negatedEnd,
                            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    binding.count.text = spannable
                } else {
                    binding.count.text = row.happenedCount.toString()
                }
            }
        }
    }
}

