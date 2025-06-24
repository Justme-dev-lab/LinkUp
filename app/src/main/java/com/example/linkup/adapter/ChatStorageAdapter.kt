package com.example.linkup.adapter // Atau package adapter Anda

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.linkup.ChatStorageDetail
import com.example.linkup.R
import com.example.linkup.databinding.ItemChatStorageBinding // Pastikan Anda membuat layout item ini

class ChatStorageAdapter(
    private val onItemClicked: (ChatStorageDetail) -> Unit
) : ListAdapter<ChatStorageDetail, ChatStorageAdapter.ChatStorageViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatStorageViewHolder {
        val binding = ItemChatStorageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatStorageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatStorageViewHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    inner class ChatStorageViewHolder(private val binding: ItemChatStorageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClicked(getItem(position))
                }
            }
        }

        fun bind(chatDetail: ChatStorageDetail) {
            binding.textViewChatName.text = chatDetail.chatName
            binding.textViewChatStorageSize.text = chatDetail.storageUsedFormatted

            Glide.with(binding.imageViewChatProfile.context)
                .load(chatDetail.recipientProfileImageUrl)
                .placeholder(R.drawable.ic_profile) // Placeholder default
                .error(R.drawable.ic_profile_error)   // Gambar jika error load
                .circleCrop()
                .into(binding.imageViewChatProfile)
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ChatStorageDetail>() {
        override fun areItemsTheSame(oldItem: ChatStorageDetail, newItem: ChatStorageDetail): Boolean {
            return oldItem.chatId == newItem.chatId
        }

        override fun areContentsTheSame(oldItem: ChatStorageDetail, newItem: ChatStorageDetail): Boolean {
            return oldItem == newItem
        }
    }
}