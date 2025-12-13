package dev.vmikk.eventtracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.res.ColorStateList
import android.content.Intent
import android.content.Context
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.vmikk.eventtracker.backup.DatabaseBackup
import dev.vmikk.eventtracker.backup.DropboxAuthManager
import dev.vmikk.eventtracker.backup.DropboxBackupScheduler
import dev.vmikk.eventtracker.backup.DropboxBackupService
import dev.vmikk.eventtracker.data.EventRepository
import dev.vmikk.eventtracker.data.EventTypeEntity
import dev.vmikk.eventtracker.databinding.FragmentSettingsBinding
import dev.vmikk.eventtracker.databinding.DialogEditEventTypeBinding
import dev.vmikk.eventtracker.export.TsvExporter
import dev.vmikk.eventtracker.settings.EventTypeAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val repo by lazy { EventRepository.from(requireContext()) }
    private val prefs by lazy { requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

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

        adapter = EventTypeAdapter(onEdit = { eventType -> showEditDialog(eventType) })
        binding.eventTypeList.layoutManager = LinearLayoutManager(requireContext())
        binding.eventTypeList.adapter = adapter

        binding.addEventType.setOnClickListener { showEditDialog(null) }
        binding.exportTsv.setOnClickListener {
            lifecycleScope.launch {
                val file = withContext(Dispatchers.IO) {
                    TsvExporter.exportAllNonEmptyDates(requireContext(), repo)
                }
                shareFile(file)
            }
        }

        binding.dailyBackupSwitch.isChecked = prefs.getBoolean("daily_backup_enabled", false)
        binding.dailyBackupSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("daily_backup_enabled", isChecked).apply()
            DropboxBackupScheduler.setDailyEnabled(requireContext(), isChecked)
        }

        fun renderCalendarMaxMarkers() {
            val n = prefs.getInt(KEY_CALENDAR_MAX_MARKERS, DEFAULT_CALENDAR_MAX_MARKERS)
            binding.calendarMaxMarkers.text = getString(R.string.calendar_max_markers_value, n)
        }

        renderCalendarMaxMarkers()
        binding.calendarMaxMarkers.setOnClickListener {
            val options = (1..6).map { it.toString() }.toTypedArray()
            val current = (prefs.getInt(KEY_CALENDAR_MAX_MARKERS, DEFAULT_CALENDAR_MAX_MARKERS) - 1)
                .coerceIn(0, options.lastIndex)
            var selected = current

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.calendar_max_markers)
                .setSingleChoiceItems(options, current) { _, which ->
                    selected = which
                }
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    prefs.edit().putInt(KEY_CALENDAR_MAX_MARKERS, selected + 1).apply()
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
            if (!DropboxAuthManager.isLinked(requireContext())) return@setOnClickListener
            DropboxBackupScheduler.enqueueImmediate(requireContext())
        }

        binding.restoreLatest.setOnClickListener {
            if (!DropboxAuthManager.isLinked(requireContext())) return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.restore_latest)
                .setMessage(getString(R.string.restore_warning))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    lifecycleScope.launch {
                        val downloaded = withContext(Dispatchers.IO) {
                            DropboxBackupService.downloadLatestBackup(requireContext())
                        }
                        if (downloaded != null) {
                            withContext(Dispatchers.IO) { DatabaseBackup.restoreFromEncryptedBackup(requireContext(), downloaded) }
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.restore_latest)
                                .setMessage(getString(R.string.restore_done))
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
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

        val n = prefs.getInt(KEY_CALENDAR_MAX_MARKERS, DEFAULT_CALENDAR_MAX_MARKERS)
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
                val name = dialogBinding.nameInput.text?.toString().orEmpty()
                val emoji = dialogBinding.emojiInput.text?.toString().orEmpty()
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        if (existing == null) {
                            repo.createEventType(name = name, colorArgb = selectedColor, emoji = emoji)
                        } else {
                            repo.updateEventType(id = existing.id, name = name, colorArgb = selectedColor, emoji = emoji)
                        }
                    }
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val PREFS_NAME = "prefs"
        const val KEY_CALENDAR_MAX_MARKERS = "calendar_max_markers"
        private const val DEFAULT_CALENDAR_MAX_MARKERS = 3
    }
}


