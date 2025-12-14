package dev.vmikk.eventtracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dev.vmikk.eventtracker.data.CustomEventEntity
import dev.vmikk.eventtracker.data.EventRepository
import dev.vmikk.eventtracker.data.EventTypeEntity
import dev.vmikk.eventtracker.databinding.BottomSheetDayEventsBinding
import dev.vmikk.eventtracker.databinding.ItemCustomEventBinding
import dev.vmikk.eventtracker.databinding.ItemDayEventToggleBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class DayEventsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetDayEventsBinding? = null
    private val binding get() = _binding!!

    private val repo by lazy { EventRepository.from(requireContext()) }

    private val date: LocalDate by lazy {
        val epochDay = requireArguments().getLong(ARG_DATE_EPOCH_DAY)
        LocalDate.ofEpochDay(epochDay)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetDayEventsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val titleFormatter =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault())
        binding.dateTitle.text = date.format(titleFormatter)

        binding.addCustomButton.setOnClickListener {
            val text = binding.customInput.text?.toString().orEmpty()
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { repo.addCustomEvent(date, text) }
                binding.customInput.setText("")
                refresh()
                notifyChanged()
            }
        }

        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            binding.progress.isVisible = true

            val eventTypes = withContext(Dispatchers.IO) { repo.getActiveEventTypesOnce() }
            val enabled = withContext(Dispatchers.IO) { repo.getEnabledEventTypeIdsOnce(date) }
            val customEvents = withContext(Dispatchers.IO) { repo.listCustomEventsOnce(date) }

            renderEventTypeToggles(eventTypes, enabled)
            renderCustomEvents(customEvents)

            binding.progress.isVisible = false
        }
    }

    private fun renderEventTypeToggles(
        eventTypes: List<EventTypeEntity>,
        enabledEventTypeIds: Set<String>,
    ) {
        binding.eventTypeContainer.removeAllViews()
        eventTypes.forEach { type ->
            val row = ItemDayEventToggleBinding.inflate(layoutInflater, binding.eventTypeContainer, false)
            row.colorDot.background.setTint(type.colorArgb)
            val label = buildString {
                if (!type.emoji.isNullOrBlank()) append(type.emoji).append(" ")
                append(type.name)
            }
            row.eventName.text = label

            row.eventSwitch.setOnCheckedChangeListener(null)
            row.eventSwitch.isChecked = enabledEventTypeIds.contains(type.id)
            row.eventSwitch.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { repo.toggleEvent(date, type.id, isChecked) }
                    notifyChanged()
                }
            }

            binding.eventTypeContainer.addView(row.root)
        }
    }

    private fun renderCustomEvents(customEvents: List<CustomEventEntity>) {
        binding.customContainer.removeAllViews()
        customEvents.forEach { ev ->
            val row = ItemCustomEventBinding.inflate(layoutInflater, binding.customContainer, false)
            row.customText.text = ev.text
            row.deleteButton.setOnClickListener {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { repo.deleteCustomEvent(ev.id) }
                    refresh()
                    notifyChanged()
                }
            }
            binding.customContainer.addView(row.root)
        }
        binding.customEmpty.isVisible = customEvents.isEmpty()
    }

    private fun notifyChanged() {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY_DAY_EVENTS_CHANGED,
            bundleOf(ARG_DATE_EPOCH_DAY to date.toEpochDay())
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val REQUEST_KEY_DAY_EVENTS_CHANGED = "dayEventsChanged"
        private const val ARG_DATE_EPOCH_DAY = "dateEpochDay"

        fun show(fm: FragmentManager, date: LocalDate) {
            DayEventsBottomSheet().apply {
                arguments = bundleOf(ARG_DATE_EPOCH_DAY to date.toEpochDay())
            }.show(fm, "DayEventsBottomSheet")
        }
    }
}




