package com.example.linkup.adapter

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.linkup.model.ChatMessageModel
import com.example.linkup.R
import de.hdodenhof.circleimageview.CircleImageView

class MessageAdapter(
    private val context: Context,
    private val messageList: List<ChatMessageModel>,
    private val currentUserId: String,
    private val recipientProfileImageUrl: String? // Tambahkan ini
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    // Di dalam MessageAdapter.kt
    companion object {
        const val MSG_TYPE_LEFT = 0
        const val MSG_TYPE_RIGHT = 1
        const val MSG_TYPE_LEFT_SOUND = 2 // Baru
        const val MSG_TYPE_RIGHT_SOUND = 3 // Baru
        // Tambahkan view type lain jika perlu (image, file, dll.)
    }

    override fun getItemViewType(position: Int): Int {
        val message = messageList[position]
        return if (message.sender == currentUserId) {
            when (message.type) {
                "sound" -> MSG_TYPE_RIGHT_SOUND
                // "image" -> MSG_TYPE_RIGHT_IMAGE
                else -> MSG_TYPE_RIGHT
            }
        } else {
            when (message.type) {
                "sound" -> MSG_TYPE_LEFT_SOUND
                // "image" -> MSG_TYPE_LEFT_IMAGE
                else -> MSG_TYPE_LEFT
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutInflater = LayoutInflater.from(context)
        val view = when (viewType) {
            MSG_TYPE_RIGHT -> layoutInflater.inflate(R.layout.chat_item_right, parent, false)
            MSG_TYPE_LEFT -> layoutInflater.inflate(R.layout.chat_item_left, parent, false)
            MSG_TYPE_RIGHT_SOUND -> layoutInflater.inflate(R.layout.chat_item_sound_right, parent, false) // Buat layout ini
            MSG_TYPE_LEFT_SOUND -> layoutInflater.inflate(R.layout.chat_item_sound_left, parent, false)   // Buat layout ini
            else -> layoutInflater.inflate(R.layout.chat_item_right, parent, false) // Default
        }
        return MessageViewHolder(view)
    }

    override fun getItemCount(): Int {
        return messageList.size
    }

    // Di MessageAdapter.kt

// ... (companion object, getItemViewType, onCreateViewHolder seperti di atas) ...

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val showMessage: TextView = itemView.findViewById(R.id.show_message)
        val profileImage: CircleImageView? = itemView.findViewById(R.id.profile_image_chat_item) // Mungkin null
        // Tambahkan view lain jika ada di layout item Anda, misal untuk pesan suara
        val playSoundButton: ImageView? = itemView.findViewById(R.id.play_sound_message_btn) // Mungkin null
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messageList[position]
        val viewType = getItemViewType(position)

        holder.showMessage.text = when (message.type) {
            "sound" -> message.soundTitle ?: "Play Sound"
            // "image" -> "View Image" // Placeholder
            else -> message.message // Pesan teks biasa
        }

        if (viewType == MSG_TYPE_LEFT || viewType == MSG_TYPE_LEFT_SOUND) { // Atau semua tipe kiri
            holder.profileImage?.let {
                if (!recipientProfileImageUrl.isNullOrEmpty()) {
                    Glide.with(context)
                        .load(recipientProfileImageUrl)
                        .placeholder(R.drawable.profile)
                        .error(R.drawable.profile)
                        .into(it)
                } else {
                    it.setImageResource(R.drawable.profile)
                }
            }
        } else {
            holder.profileImage?.visibility = View.GONE // Sembunyikan untuk pesan kanan
        }

        // Penanganan untuk tombol play pada pesan suara
        if (message.type == "sound") {
            holder.playSoundButton?.setOnClickListener {
                // Implementasi pemutaran suara di sini
                // Anda mungkin perlu instance MediaPlayer atau ExoPlayer di adapter atau Activity
                message.fileUrl?.let { url ->
                    playSoundUrl(url) // Buat fungsi ini
                }
            }
        } else {
            holder.playSoundButton?.visibility = View.GONE // Sembunyikan jika bukan pesan suara
        }
    }

    // Fungsi untuk memutar suara (bisa diletakkan di adapter atau dipanggil ke activity)
    private var chatMediaPlayer: MediaPlayer? = null
    private fun playSoundUrl(url: String) {
        try {
            chatMediaPlayer?.release() // Hentikan pemutaran sebelumnya
            chatMediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener { start() }
                setOnCompletionListener {
                    it.release()
                    chatMediaPlayer = null
                }
                setOnErrorListener { _, _, _ ->
                    Toast.makeText(context, "Cannot play sound", Toast.LENGTH_SHORT).show()
                    chatMediaPlayer?.release()
                    chatMediaPlayer = null
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error playing sound: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("MessageAdapter", "Error playing sound URL: $url", e)
        }
    }

// Jangan lupa untuk melepaskan MediaPlayer saat activity/fragment dihancurkan
// Anda mungkin ingin menambahkan fungsi di MessageChatActivity untuk mengontrol MediaPlayer ini
// dan memanggilnya dari adapter, atau menggunakan library audio player yang lebih canggih.

// ... (getItemCount tetap sama) ...
}