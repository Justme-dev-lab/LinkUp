package com.example.linkup.ui.chats

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.linkup.R
import com.example.linkup.model.Chat
import com.example.linkup.utils.DateUtils.formatTimestamp
import java.util.Locale

class ChatAdapter(
    private var chatList: MutableList<Chat>,
    private val onItemClick: (Chat) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>(), Filterable {

    private var chatListFull = chatList.toMutableList()

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: ImageView = itemView.findViewById(R.id.profileImage)
        val name: TextView = itemView.findViewById(R.id.nameTextView)
        val lastMessage: TextView = itemView.findViewById(R.id.lastMessageTextView)
        val time: TextView = itemView.findViewById(R.id.timeTextView)
        val statusIndicator: ImageView = itemView.findViewById(R.id.statusIndicator)

        fun bind(chat: Chat) {
            itemView.setOnClickListener { onItemClick(chat) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chatList[position]

        holder.name.text = chat.recipientName
        holder.time.text = formatTimestamp(chat.lastMessageTime)

        if (chat.lastMessage.isNullOrEmpty()) {
            holder.lastMessage.text = "Ayo mulai mengobrol"
        } else {
            holder.lastMessage.text = chat.lastMessage
        }

        // Load profile image
        Glide.with(holder.itemView.context)
            .load(chat.recipientProfileImage)
            .placeholder(R.drawable.ic_profile)
            .into(holder.profileImage)

        // Show read status if last message was sent by current user
        if (chat.lastMessageSender == ChatsViewModel().getCurrentUserId()) {
            val statusRes = when (chat.lastMessageStatus) {
                "read" -> R.drawable.ic_read
                "delivered" -> R.drawable.ic_delivered
                else -> R.drawable.ic_sent
            }
            holder.statusIndicator.setImageResource(statusRes)
            holder.statusIndicator.visibility = View.VISIBLE
        } else {
            holder.statusIndicator.visibility = View.GONE
        }

        holder.bind(chat)
    }

    override fun getItemCount() = chatList.size

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filteredList = mutableListOf<Chat>()
                if (constraint.isNullOrEmpty()) {
                    filteredList.addAll(chatListFull)
                } else {
                    val filterPattern = constraint.toString().lowercase(Locale.getDefault()).trim()
                    chatListFull.forEach {
                        if (it.recipientName?.lowercase(Locale.getDefault())?.contains(filterPattern) == true) {
                            filteredList.add(it)
                        }
                    }
                }
                val results = FilterResults()
                results.values = filteredList
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                chatList.clear()
                chatList.addAll(results?.values as List<Chat>)
                notifyDataSetChanged()
            }
        }
    }

    fun updateChats(newChats: List<Chat>) {
        chatList.clear()
        chatList.addAll(newChats)
        chatListFull.clear()
        chatListFull.addAll(newChats)
        notifyDataSetChanged()
    }
}