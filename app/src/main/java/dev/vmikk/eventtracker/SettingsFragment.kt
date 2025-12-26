package dev.vmikk.eventtracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.res.ColorStateList
import android.content.Intent
import android.content.Context
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.vmikk.eventtracker.backup.BackupResult
import dev.vmikk.eventtracker.backup.DatabaseBackup
import dev.vmikk.eventtracker.backup.DropboxAuthManager
import dev.vmikk.eventtracker.backup.DropboxBackupScheduler
import dev.vmikk.eventtracker.backup.DropboxBackupService
import dev.vmikk.eventtracker.data.EventRepository
import dev.vmikk.eventtracker.data.EventTypeEntity
import dev.vmikk.eventtracker.data.PrefsManager
import dev.vmikk.eventtracker.databinding.FragmentSettingsBinding
import dev.vmikk.eventtracker.databinding.DialogEditEventTypeBinding
import dev.vmikk.eventtracker.export.TsvExporter
import dev.vmikk.eventtracker.settings.EventTypeAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val repo by lazy { EventRepository.from(requireContext()) }
    private val prefs by lazy { PrefsManager.getPrefs(requireContext()) }

    private lateinit var adapter: EventTypeAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = EventTypeAdapter(
            onEdit = { eventType -> showEditDialog(eventType) },
            onDelete = { eventType -> showDeleteDialog(eventType) }
        )
        binding.eventTypeList.layoutManager = LinearLayoutManager(requireContext())
        binding.eventTypeList.adapter = adapter

        binding.addEventType.setOnClickListener { showEditDialog(null) }
        binding.exportTsv.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val file = withContext(Dispatchers.IO) {
                        TsvExporter.exportAllNonEmptyDates(requireContext(), repo)
                    }
                    shareFile(file)
                } catch (e: Exception) {
                    android.util.Log.e("SettingsFragment", "Error exporting TSV", e)
                    com.google.android.material.snackbar.Snackbar.make(
                        binding.root,
                        getString(R.string.error_exporting),
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }

        binding.dailyBackupSwitch.isChecked = prefs.getBoolean(PrefsManager.KEY_DAILY_BACKUP_ENABLED, PrefsManager.DEFAULT_DAILY_BACKUP_ENABLED)
        binding.dailyBackupSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit {
                putBoolean(PrefsManager.KEY_DAILY_BACKUP_ENABLED, isChecked)
            }
            DropboxBackupScheduler.setDailyEnabled(requireContext(), isChecked)
        }

        fun renderCalendarMaxMarkers() {
            val n = prefs.getInt(PrefsManager.KEY_CALENDAR_MAX_MARKERS, PrefsManager.DEFAULT_CALENDAR_MAX_MARKERS)
            binding.calendarMaxMarkers.text = getString(R.string.calendar_max_markers_value, n)
        }

        renderCalendarMaxMarkers()
        binding.calendarMaxMarkers.setOnClickListener {
            val options = (1..6).map { it.toString() }.toTypedArray()
            val current = (prefs.getInt(PrefsManager.KEY_CALENDAR_MAX_MARKERS, PrefsManager.DEFAULT_CALENDAR_MAX_MARKERS) - 1)
                .coerceIn(0, options.lastIndex)
            var selected = current

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.calendar_max_markers)
                .setSingleChoiceItems(options, current) { _, which ->
                    selected = which
                }
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    prefs.edit {
                        putInt(PrefsManager.KEY_CALENDAR_MAX_MARKERS, selected + 1)
                    }
                    renderCalendarMaxMarkers()
                }
                .show()
        }

        binding.connectDropbox.setOnClickListener {
            if (!DropboxAuthManager.isConfigured(requireContext())) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.connect_dropbox)
                    .setMessage(getString(R.string.dropbox_setup_instructions))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@setOnClickListener
            }
            DropboxAuthManager.startLink(requireContext())
        }

        binding.backupNow.setOnClickListener {
            if (!DropboxAuthManager.isLinked(requireContext())) {
                com.google.android.material.snackbar.Snackbar.make(
                    binding.root,
                    getString(R.string.error_auth),
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            binding.backupNow.isEnabled = false
            lifecycleScope.launch {
                try {
                    DropboxBackupScheduler.enqueueImmediate(requireContext())
                    com.google.android.material.snackbar.Snackbar.make(
                        binding.root,
                        getString(R.string.backup_success),
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    android.util.Log.e("SettingsFragment", "Error initiating backup", e)
                    com.google.android.material.snackbar.Snackbar.make(
                        binding.root,
                        getString(R.string.error_backup),
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                    ).show()
                } finally {
                    binding.backupNow.isEnabled = true
                }
            }
        }

        binding.restoreLatest.setOnClickListener {
            if (!DropboxAuthManager.isLinked(requireContext())) {
                com.google.android.material.snackbar.Snackbar.make(
                    binding.root,
                    getString(R.string.error_auth),
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.restore_latest)
                .setMessage(getString(R.string.restore_warning))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    binding.restoreLatest.isEnabled = false
                    lifecycleScope.launch {
                        try {
                            val result = DropboxBackupService.downloadLatestBackup(requireContext())
                            when (result) {
                                is BackupResult.Success -> {
                                    val file = result.file
                                    if (file != null) {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                DatabaseBackup.restoreFromEncryptedBackup(requireContext(), file)
                                            }
                                            MaterialAlertDialogBuilder(requireContext())
                                                .setTitle(R.string.restore_latest)
                                                .setMessage(getString(R.string.restore_done))
                                                .setPositiveButton(android.R.string.ok, null)
                                                .show()
                                        } catch (e: Exception) {
                                            android.util.Log.e("SettingsFragment", "Error restoring backup", e)
                                            com.google.android.material.snackbar.Snackbar.make(
                                                binding.root,
                                                getString(R.string.error_restore),
                                                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                                            ).show()
                                        }
                                    } else {
                                        com.google.android.material.snackbar.Snackbar.make(
                                            binding.root,
                                            getString(R.string.error_restore),
                                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                is BackupResult.Error -> {
                                    com.google.android.material.snackbar.Snackbar.make(
                                        binding.root,
                                        result.message,
                                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SettingsFragment", "Error restoring backup", e)
                            com.google.android.material.snackbar.Snackbar.make(
                                binding.root,
                                getString(R.string.error_restore),
                                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                            ).show()
                        } finally {
                            binding.restoreLatest.isEnabled = true
                        }
                    }
                }
                .show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.observeActiveEventTypes().collectLatest { list ->
                    adapter.submitList(list)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val linked = DropboxAuthManager.isLinked(requireContext())
        binding.connectDropbox.isEnabled = !linked
        binding.connectDropbox.text = if (linked) getString(R.string.dropbox_connected) else getString(R.string.connect_dropbox)

        val n = prefs.getInt(PrefsManager.KEY_CALENDAR_MAX_MARKERS, PrefsManager.DEFAULT_CALENDAR_MAX_MARKERS)
        binding.calendarMaxMarkers.text = getString(R.string.calendar_max_markers_value, n)
    }

    private fun shareFile(file: java.io.File) {
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/tab-separated-values"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.export_tsv)))
    }

    private fun showEditDialog(existing: EventTypeEntity?) {
        val dialogBinding = DialogEditEventTypeBinding.inflate(layoutInflater)
        dialogBinding.nameInput.setText(existing?.name.orEmpty())
        dialogBinding.emojiInput.setText(existing?.emoji.orEmpty())

        val colors = listOf(
            0xFF1E88E5.toInt(), // blue
            0xFF43A047.toInt(), // green
            0xFFF4511E.toInt(), // orange
            0xFF8E24AA.toInt(), // purple
            0xFFE53935.toInt(), // red
            0xFF3949AB.toInt(), // indigo
            0xFF00897B.toInt(), // teal
            0xFF6D4C41.toInt(), // brown
        )

        val initialColor = existing?.colorArgb ?: colors.first()
        var selectedColor = initialColor

        dialogBinding.colorGroup.removeAllViews()
        colors.forEach { color ->
            val chip = Chip(requireContext()).apply {
                isCheckable = true
                text = " "
                chipBackgroundColor = ColorStateList.valueOf(color)
                // Keep chips compact; still comfortably tappable in the dialog.
                isCheckedIconVisible = true
                isChecked = color == initialColor
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedColor = color
                }
            }
            dialogBinding.colorGroup.addView(chip)
        }

        val title = if (existing == null) getString(R.string.add_event_type) else getString(R.string.edit_event_type)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = dialogBinding.nameInput.text?.toString().orEmpty().trim()
                val emoji = dialogBinding.emojiInput.text?.toString().orEmpty().trim()
                
                // Validate input
                if (name.isBlank()) {
                    com.google.android.material.snackbar.Snackbar.make(
                        binding.root,
                        "Event name cannot be empty",
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                
                if (name.length > 50) {
                    com.google.android.material.snackbar.Snackbar.make(
                        binding.root,
                        "Event name is too long (max 50 characters)",
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            if (existing == null) {
                                repo.createEventType(name = name, colorArgb = selectedColor, emoji = emoji)
                            } else {
                                repo.updateEventType(id = existing.id, name = name, colorArgb = selectedColor, emoji = emoji)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SettingsFragment", "Error saving event type", e)
                        com.google.android.material.snackbar.Snackbar.make(
                            binding.root,
                            getString(R.string.error_saving_event),
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .show()
    }

    private fun showDeleteDialog(eventType: EventTypeEntity) {
        lifecycleScope.launch {
            try {
                val isInUse = withContext(Dispatchers.IO) {
                    repo.isEventTypeInUse(eventType.id)
                }

                val message = if (isInUse) {
                    getString(R.string.delete_event_type_confirm_with_usage, eventType.name)
                } else {
                    getString(R.string.delete_event_type_confirm, eventType.name)
                }

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.delete_event_type)
                    .setMessage(message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        lifecycleScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    repo.deleteEventType(eventType.id)
                                }
                                // Notify other fragments that events have changed
                                parentFragmentManager.setFragmentResult(
                                    DayEventsBottomSheet.REQUEST_KEY_DAY_EVENTS_CHANGED,
                                    bundleOf(DayEventsBottomSheet.ARG_DATE_EPOCH_DAY to LocalDate.now().toEpochDay())
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("SettingsFragment", "Error deleting event type", e)
                                com.google.android.material.snackbar.Snackbar.make(
                                    binding.root,
                                    getString(R.string.error_deleting_event),
                                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    .show()
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Error checking event type usage", e)
                com.google.android.material.snackbar.Snackbar.make(
                    binding.root,
                    getString(R.string.error_loading_events),
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}

