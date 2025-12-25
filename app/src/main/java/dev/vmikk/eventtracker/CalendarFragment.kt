package dev.vmikk.eventtracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import dev.vmikk.eventtracker.data.EventRepository
import dev.vmikk.eventtracker.databinding.FragmentCalendarBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import android.content.Context

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private var currentMonth: YearMonth = YearMonth.now()

    private lateinit var adapter: MonthGridAdapter
    private val markerCache: MutableMap<LocalDate, DayCellData> = mutableMapOf()
    private val prefs by lazy { requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    private var baseGridPaddingTop: Int = 0
    private var baseGridPaddingBottom: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repo = EventRepository.from(requireContext())

        parentFragmentManager.setFragmentResultListener(
            DayEventsBottomSheet.REQUEST_KEY_DAY_EVENTS_CHANGED,
            viewLifecycleOwner
        ) { _, _ ->
            lifecycleScope.launch { loadMonthMarkers(repo) }
        }

        adapter = MonthGridAdapter(
            onDateClick = { date ->
                DayEventsBottomSheet.show(parentFragmentManager, date)
            },
            dayCellForDate = { date -> markerCache[date] ?: DayCellData() }
        )
        adapter.maxMarkersPerDay = prefs.getInt(SettingsFragment.KEY_CALENDAR_MAX_MARKERS, 3)

        binding.monthGrid.layoutManager = GridLayoutManager(requireContext(), 7)
        binding.monthGrid.adapter = adapter
        baseGridPaddingTop = binding.monthGrid.paddingTop
        baseGridPaddingBottom = binding.monthGrid.paddingBottom

        binding.prevMonth.setOnClickListener {
            currentMonth = currentMonth.minusMonths(1)
            renderMonth()
            lifecycleScope.launch { loadMonthMarkers(repo) }
        }
        binding.nextMonth.setOnClickListener {
            currentMonth = currentMonth.plusMonths(1)
            renderMonth()
            lifecycleScope.launch { loadMonthMarkers(repo) }
        }

        binding.addTodayFab.setOnClickListener {
            DayEventsBottomSheet.show(parentFragmentManager, LocalDate.now())
        }

        renderMonth()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) { repo.ensureDefaultEventTypesIfEmpty() }
            loadMonthMarkers(repo)
        }
    }

    private suspend fun loadMonthMarkers(repo: EventRepository) {
        val markers = withContext(Dispatchers.IO) { repo.getMonthMarkers(currentMonth) }
        // Ensure we're on main thread for UI updates
        withContext(Dispatchers.Main) {
            if (!isAdded || view == null) return@withContext
            markerCache.clear()
            markerCache.putAll(markers)
            // Post to ensure RecyclerView updates after any pending layout passes
            binding.monthGrid.post {
                if (isAdded && view != null) {
                    adapter.notifyDateCellsChanged()
                }
            }
        }
    }

    private fun renderMonth() {
        val headerFormatter =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.getDefault())
        val monthTitle = currentMonth.atDay(1).format(headerFormatter)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        binding.monthTitle.text = monthTitle

        adapter.submitMonth(currentMonth)
        applyGridSizing()
    }

    override fun onResume() {
        super.onResume()
        val newMax = prefs.getInt(SettingsFragment.KEY_CALENDAR_MAX_MARKERS, 3)
        if (adapter.maxMarkersPerDay != newMax) {
            adapter.maxMarkersPerDay = newMax
            adapter.notifyDateCellsChanged()
        }
        applyGridSizing()
    }

    private fun applyGridSizing() {
        // Post so monthGrid has a measured height.
        binding.monthGrid.post {
            val rows = monthRowCount(currentMonth)
            val availableHeight = binding.monthGrid.height - baseGridPaddingTop - baseGridPaddingBottom
            if (rows <= 0 || availableHeight <= 0) return@post

            val minCellHeightPx = dpToPx(56f)
            val maxCellHeightPx = dpToPx(80f)
            val idealCellHeightPx = (availableHeight / rows).coerceIn(minCellHeightPx, maxCellHeightPx)

            if (adapter.cellHeightPx != idealCellHeightPx) {
                adapter.cellHeightPx = idealCellHeightPx
                adapter.notifyAllCellsChanged()
            }

            val used = idealCellHeightPx * rows
            val extra = (availableHeight - used).coerceAtLeast(0)
            val addTop = extra / 2
            val addBottom = extra - addTop
            binding.monthGrid.setPadding(
                binding.monthGrid.paddingLeft,
                baseGridPaddingTop + addTop,
                binding.monthGrid.paddingRight,
                baseGridPaddingBottom + addBottom
            )
        }
    }

    private fun monthRowCount(month: YearMonth): Int {
        val first = month.atDay(1)
        val totalDays = month.lengthOfMonth()
        val leadingBlanks = ((first.dayOfWeek.value - 1) + 7) % 7 // Monday-start
        return ((leadingBlanks + totalDays + 6) / 7).coerceAtLeast(1)
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


