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
import dev.vmikk.eventtracker.data.DayEventEntity
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
                try {
                    val added = withContext(Dispatchers.IO) { repo.addCustomEvent(date, text) }
                    if (added) {
                        binding.customInput.setText("")
                        refresh()
                        notifyChanged()
                    } else {
                        // Show feedback for duplicate
                        com.google.android.material.snackbar.Snackbar.make(
                            binding.root,
                            getString(R.string.custom_event_duplicate),
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DayEventsBottomSheet", "Error adding custom event", e)
                    com.google.android.material.snackbar.Snackbar.make(
                        binding.root,
                        getString(R.string.error_saving_event),
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }

        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            binding.progress.isVisible = true
            try {
                val eventTypes = withContext(Dispatchers.IO) { repo.getActiveEventTypesOnce() }
                val eventStates = withContext(Dispatchers.IO) { repo.getEventTypeStatesOnce(date) }
                val customEvents = withContext(Dispatchers.IO) { repo.listCustomEventsOnce(date) }

                renderEventTypeToggles(eventTypes, eventStates)
                renderCustomEvents(customEvents)
            } catch (e: Exception) {
                android.util.Log.e("DayEventsBottomSheet", "Error loading events", e)
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

    private fun renderEventTypeToggles(
        eventTypes: List<EventTypeEntity>,
        eventStates: Map<String, Int>,
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

            val currentState = eventStates[type.id]
            val isHappened = currentState == DayEventEntity.STATE_HAPPENED
            val isNegated = currentState == DayEventEntity.STATE_NEGATED

            // Visual feedback for negated state
            if (isNegated) {
                row.eventName.alpha = 0.5f
                row.negateButton.alpha = 1.0f
                row.negateButton.imageTintList = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.grey_500)
                )
            } else {
                row.eventName.alpha = 1.0f
                row.negateButton.alpha = 0.6f
                row.negateButton.imageTintList = null // Use default tint
            }

            // Switch: controls happened state
            row.eventSwitch.setOnCheckedChangeListener(null)
            row.eventSwitch.isChecked = isHappened
            row.eventSwitch.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    try {
                        if (isChecked) {
                            // Switch ON -> set to HAPPENED, clear negate
                            withContext(Dispatchers.IO) {
                                repo.setEventState(date, type.id, DayEventEntity.STATE_HAPPENED)
                            }
                        } else {
                            // Switch OFF -> clear state (delete row)
                            withContext(Dispatchers.IO) {
                                repo.setEventState(date, type.id, null)
                            }
                        }
                        refresh()
                        notifyChanged()
                    } catch (e: Exception) {
                        android.util.Log.e("DayEventsBottomSheet", "Error updating event state", e)
                        com.google.android.material.snackbar.Snackbar.make(
                            binding.root,
                            getString(R.string.error_saving_event),
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                        ).show()
                        // Revert switch state on error
                        row.eventSwitch.setOnCheckedChangeListener(null)
                        row.eventSwitch.isChecked = !isChecked
                        row.eventSwitch.setOnCheckedChangeListener { _, _ ->
                            // Re-attach listener
                        }
                    }
                }
            }

            // Negate button: controls negated state
            row.negateButton.setOnClickListener {
                lifecycleScope.launch {
                    try {
                        if (isNegated) {
                            // Negate button ON -> turn it OFF (clear state)
                            withContext(Dispatchers.IO) {
                                repo.setEventState(date, type.id, null)
                            }
                        } else {
                            // Negate button OFF -> set to NEGATED, force switch OFF
                            withContext(Dispatchers.IO) {
                                repo.setEventState(date, type.id, DayEventEntity.STATE_NEGATED)
                            }
                        }
                        refresh()
                        notifyChanged()
                    } catch (e: Exception) {
                        android.util.Log.e("DayEventsBottomSheet", "Error updating event state", e)
                        com.google.android.material.snackbar.Snackbar.make(
                            binding.root,
                            getString(R.string.error_saving_event),
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                        ).show()
                    }
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
                    try {
                        withContext(Dispatchers.IO) { repo.deleteCustomEvent(ev.id) }
                        refresh()
                        notifyChanged()
                    } catch (e: Exception) {
                        android.util.Log.e("DayEventsBottomSheet", "Error deleting custom event", e)
                        com.google.android.material.snackbar.Snackbar.make(
                            binding.root,
                            getString(R.string.error_deleting_event),
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                        ).show()
                    }
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
        const val ARG_DATE_EPOCH_DAY = "dateEpochDay"

        fun show(fm: FragmentManager, date: LocalDate) {
            DayEventsBottomSheet().apply {
                arguments = bundleOf(ARG_DATE_EPOCH_DAY to date.toEpochDay())
            }.show(fm, "DayEventsBottomSheet")
        }
    }
}
