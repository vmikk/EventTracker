package dev.vmikk.eventtracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
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

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private var currentMonth: YearMonth = YearMonth.now()

    private lateinit var adapter: MonthGridAdapter
    private val markerCache: MutableMap<LocalDate, List<DayMarker>> = mutableMapOf()

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
            markersForDate = { date -> markerCache[date].orEmpty() }
        )

        binding.monthGrid.layoutManager = GridLayoutManager(requireContext(), 7)
        binding.monthGrid.adapter = adapter

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

        renderMonth()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) { repo.ensureDefaultEventTypesIfEmpty() }
            loadMonthMarkers(repo)
        }
    }

    private suspend fun loadMonthMarkers(repo: EventRepository) {
        val markers = withContext(Dispatchers.IO) { repo.getMonthMarkers(currentMonth) }
        markerCache.clear()
        markerCache.putAll(markers)
        adapter.notifyDataSetChanged()
    }

    private fun renderMonth() {
        val headerFormatter =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.getDefault())
        val monthTitle = currentMonth.atDay(1).format(headerFormatter)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        binding.monthTitle.text = monthTitle

        adapter.submitMonth(currentMonth)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


