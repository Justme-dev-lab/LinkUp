package com.example.linkup.ui.chats

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.linkup.R
import com.example.linkup.model.Chat
import com.example.linkup.utils.DateUtils.formatTimestamp
import java.util.Locale

class ChatAdapter(
    private val currentUserId: String?,
    private val onItemClick: (Chat) -> Unit
) : ListAdapter<Chat, ChatAdapter.ChatViewHolder>(ChatDiffCallback()), Filterable {

    private var originalList: List<Chat> = emptyList()

    fun updateChats(newChats: List<Chat>) {
        originalList = newChats
        submitList(newChats)
    }

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val profileImage: ImageView = itemView.findViewById(R.id.profileImage)
        private val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
        private val lastMessageTextView: TextView = itemView.findViewById(R.id.lastMessageTextView)
        private val timeTextView: TextView = itemView.findViewById(R.id.timeTextView)
        private val statusIndicator: ImageView = itemView.findViewById(R.id.statusIndicator)

        fun bind(chat: Chat) {
            itemView.setOnClickListener { onItemClick(chat) }

            nameTextView.text = chat.recipientName ?: "Loading..."
            timeTextView.text = if (chat.lastMessageTime > 0) formatTimestamp(chat.lastMessageTime) else ""

            if (chat.lastMessage.isNullOrEmpty()) {
                lastMessageTextView.text = "Ayo mulai mengobrol"
            } else {
                lastMessageTextView.text = chat.lastMessage
            }

            Glide.with(itemView.context)
                .load(chat.recipientProfileImage)
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .into(profileImage)

            // ------------------ AWAL BAGIAN PENTING DENGAN LOGGING ------------------
            // Pastikan currentUserId tidak null sebelum perbandingan
            val isMyMessage = currentUserId != null && chat.lastMessageSenderId == currentUserId

            // LOGGING KRUSIAL:
            Log.d("ChatAdapterDebug", "-----------------------------------------------------")
            Log.d("ChatAdapterDebug", "Binding chat ID: ${chat.id}")
            Log.d("ChatAdapterDebug", "Last Message: \"${chat.lastMessage}\"")
            Log.d("ChatAdapterDebug", "Adapter currentUserId: $currentUserId")
            Log.d("ChatAdapterDebug", "Chat lastMessageSenderId: ${chat.lastMessageSenderId}")
            Log.d("ChatAdapterDebug", "Is My Message? $isMyMessage")
            Log.d("ChatAdapterDebug", "Chat lastMessageStatus: ${chat.lastMessageStatus}")


            if (isMyMessage && !chat.lastMessage.isNullOrEmpty()) {
                statusIndicator.visibility = View.VISIBLE
                Log.d("ChatAdapterDebug", "VISEME: Pesan dari SAYA. Menampilkan status.")

                val statusRes = when (chat.lastMessageStatus?.lowercase(Locale.getDefault())) {
                    "read" -> R.drawable.ic_read
                    "delivered" -> R.drawable.ic_delivered
                    else -> R.drawable.ic_sent
                }
                statusIndicator.setImageResource(statusRes)
            } else {
                statusIndicator.visibility = View.GONE
                Log.d("ChatAdapterDebug", "VISEME: Pesan dari LAWAN atau KOSONG. Menyembunyikan status.")
            }
            Log.d("ChatAdapterDebug", "Final statusIndicator visibility: ${statusIndicator.visibility == View.VISIBLE}")
            Log.d("ChatAdapterDebug", "-----------------------------------------------------")
            // ------------------- AKHIR BAGIAN PENTING DENGAN LOGGING -------------------
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filteredList = mutableListOf<Chat>()
                if (constraint.isNullOrEmpty()) {
                    filteredList.addAll(originalList)
                } else {
                    val filterPattern = constraint.toString().lowercase(Locale.getDefault()).trim()
                    originalList.forEach { chat ->
                        if (chat.recipientName?.lowercase(Locale.getDefault())?.contains(filterPattern) == true) {
                            filteredList.add(chat)
                        }
                    }
                }
                val results = FilterResults()
                results.values = filteredList
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                val newFilteredList = results?.values as? List<Chat> ?: emptyList()
                submitList(newFilteredList)
            }
        }
    }
}

class ChatDiffCallback : DiffUtil.ItemCallback<Chat>() {
    override fun areItemsTheSame(oldItem: Chat, newItem: Chat): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Chat, newItem: Chat): Boolean {
        return oldItem == newItem
    }
}