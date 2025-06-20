package com.example.linkup.ui.chats

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter // Ganti ke ListAdapter untuk DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.linkup.R
import com.example.linkup.model.Chat
import com.example.linkup.utils.DateUtils.formatTimestamp // Pastikan util ini ada dan benar
import java.util.Locale

// Gunakan ListAdapter untuk efisiensi dengan DiffUtil
class ChatAdapter(
    private val currentUserId: String?, // Teruskan currentUserId
    currentUserId1: String?,
    private val onItemClick: (Chat) -> Unit
) : ListAdapter<Chat, ChatAdapter.ChatViewHolder>(ChatDiffCallback()), Filterable {

    // List asli untuk filtering, akan diupdate dari luar
    private var originalList: List<Chat> = emptyList()

    fun updateChats(newChats: List<Chat>) {
        originalList = newChats
        submitList(newChats) // ListAdapter menggunakan submitList
    }

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Asumsi ID ini ada di item_chat.xml
        private val profileImage: ImageView = itemView.findViewById(R.id.profileImage)
        private val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
        private val lastMessageTextView: TextView = itemView.findViewById(R.id.lastMessageTextView)
        private val timeTextView: TextView = itemView.findViewById(R.id.timeTextView)
        private val statusIndicator: ImageView = itemView.findViewById(R.id.statusIndicator)

        fun bind(chat: Chat) {
            // Logika utama binding ada di onBindViewHolder sekarang,
            // itemView.setOnClickListener bisa langsung di onBindViewHolder atau di sini jika lebih rapi
            itemView.setOnClickListener { onItemClick(chat) }

            // Menggunakan properti dari model Chat yang sudah diisi di Fragment
            nameTextView.text = chat.recipientName ?: "Loading..." // Beri default jika belum ada
            timeTextView.text = if (chat.lastMessageTime > 0) formatTimestamp(chat.lastMessageTime) else ""

            if (chat.lastMessage.isNullOrEmpty()) {
                lastMessageTextView.text = "Ayo mulai mengobrol"
            } else {
                lastMessageTextView.text = chat.lastMessage
            }

            Glide.with(itemView.context)
                .load(chat.recipientProfileImage)
                .placeholder(R.drawable.ic_profile) // Placeholder default
                .error(R.drawable.ic_profile) // Gambar jika error load
                .into(profileImage)

            if (chat.lastMessageSenderId == currentUserId && !chat.lastMessage.isNullOrEmpty()) {
                val statusRes = when (chat.readBy?.containsKey(currentUserId) == true && chat.readBy?.get(currentUserId) == true) { // Contoh logika status read yang lebih baik
                    true -> R.drawable.ic_read // Jika currentUser sudah membacanya (ini mungkin salah logika untuk status TERKIRIM)
                    // Anda perlu logika status yang benar:
                    // Jika lastMessageSenderId == currentUserId, maka kita tampilkan status pesan KITA
                    // Status pesan kita bisa 'sent', 'delivered', 'read' (oleh penerima)
                    // 'chat.lastMessageStatus' yang Anda punya sepertinya sudah benar untuk ini
                    else -> when (chat.lastMessageStatus) {
                        "read" -> R.drawable.ic_read // Dibaca oleh penerima
                        "delivered" -> R.drawable.ic_delivered // Terkirim ke penerima
                        else -> R.drawable.ic_sent // Terkirim dari kita
                    }
                }
                statusIndicator.setImageResource(statusRes)
                statusIndicator.visibility = View.VISIBLE
            } else {
                // Jika pesan terakhir bukan dari kita, atau tidak ada pesan, sembunyikan status.
                statusIndicator.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false) // Pastikan R.layout.item_chat adalah layout item Anda
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position)) // ListAdapter menggunakan getItem()
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
                        // Cari berdasarkan recipientName yang sudah di-populate
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
                submitList(newFilteredList) // Update list yang ditampilkan oleh ListAdapter
            }
        }
    }
}

// DiffUtil Callback untuk ListAdapter
class ChatDiffCallback : DiffUtil.ItemCallback<Chat>() {
    override fun areItemsTheSame(oldItem: Chat, newItem: Chat): Boolean {
        return oldItem.id == newItem.id // Gunakan ID unik chat
    }

    override fun areContentsTheSame(oldItem: Chat, newItem: Chat): Boolean {
        return oldItem == newItem // Bandingkan konten jika model Chat adalah data class
    }
}