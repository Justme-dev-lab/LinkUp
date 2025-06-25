package com.example.linkup.adapter // atau package yang sesuai

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.linkup.R
import com.example.linkup.databinding.ItemChatSelectableForArchiveBinding
import com.example.linkup.model.SelectableChatItem

class SelectableChatAdapter(
    private val onItemClick: (SelectableChatItem) -> Unit
) : ListAdapter<SelectableChatItem, SelectableChatAdapter.SelectableChatViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectableChatViewHolder {
        val binding = ItemChatSelectableForArchiveBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SelectableChatViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: SelectableChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SelectableChatViewHolder(
        private val binding: ItemChatSelectableForArchiveBinding,
        private val onItemClick: (SelectableChatItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SelectableChatItem) {
            binding.textViewChatNameSelect.text = item.chatName ?: "Chat"
            binding.textViewLastMessageSelect.text = item.lastMessage ?: "No messages yet" // Atau kosongkan
            binding.checkBoxArchiveSelect.isChecked = item.isSelected

            if (!item.profileImageUrl.isNullOrEmpty()) {
                Glide.with(binding.imageViewChatProfileSelect.context)
                    .load(item.profileImageUrl)
                    .placeholder(R.drawable.ic_profile) // Placeholder default Anda
                    .error(R.drawable.ic_profile_error) // Gambar error Anda
                    .into(binding.imageViewChatProfileSelect)
            } else {
                binding.imageViewChatProfileSelect.setImageResource(R.drawable.ic_profile) // Gambar default
            }

            // Klik pada seluruh item akan mentoggle CheckBox
            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SelectableChatItem>() {
        override fun areItemsTheSame(oldItem: SelectableChatItem, newItem: SelectableChatItem): Boolean {
            return oldItem.chatId == newItem.chatId
        }

        override fun areContentsTheSame(oldItem: SelectableChatItem, newItem: SelectableChatItem): Boolean {
            return oldItem == newItem // Karena SelectableChatItem adalah data class
        }
    }
}