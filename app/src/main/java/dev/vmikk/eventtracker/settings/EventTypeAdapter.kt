package dev.vmikk.eventtracker.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.vmikk.eventtracker.data.EventTypeEntity
import dev.vmikk.eventtracker.databinding.ItemEventTypeBinding

class EventTypeAdapter(
    private val onEdit: (EventTypeEntity) -> Unit,
) : ListAdapter<EventTypeEntity, EventTypeAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemEventTypeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(
        private val binding: ItemEventTypeBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: EventTypeEntity) {
            binding.colorDot.background.setTint(item.colorArgb)
            binding.emoji.text = item.emoji.orEmpty()
            binding.emoji.alpha = if (item.emoji.isNullOrBlank()) 0.3f else 1f
            binding.name.text = item.name
            binding.editButton.setOnClickListener { onEdit(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<EventTypeEntity>() {
            override fun areItemsTheSame(oldItem: EventTypeEntity, newItem: EventTypeEntity): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: EventTypeEntity, newItem: EventTypeEntity): Boolean =
                oldItem == newItem
        }
    }
}




