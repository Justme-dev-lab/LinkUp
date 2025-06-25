package com.example.linkup.adapter

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.linkup.R
import com.example.linkup.model.ChatMessageModel
import de.hdodenhof.circleimageview.CircleImageView

class MessageAdapter(
    private val context: Context,
    private val messageList: List<ChatMessageModel>,
    private val currentUserId: String,
    private val recipientProfileImageUrl: String?
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    companion object {
        const val MSG_TYPE_LEFT_TEXT = 0
        const val MSG_TYPE_RIGHT_TEXT = 1
        const val MSG_TYPE_LEFT_SOUND = 2
        const val MSG_TYPE_RIGHT_SOUND = 3
        const val MSG_TYPE_LEFT_IMAGE = 4
        const val MSG_TYPE_RIGHT_IMAGE = 5
        const val MSG_TYPE_LEFT_VIDEO = 6 // Anda akan membuat layout dan logic untuk ini
        const val MSG_TYPE_RIGHT_VIDEO = 7 // Anda akan membuat layout dan logic untuk ini
        const val MSG_TYPE_LEFT_FILE = 8  // Anda akan membuat layout dan logic untuk ini
        const val MSG_TYPE_RIGHT_FILE = 9 // Anda akan membuat layout dan logic untuk ini
        const val MSG_TYPE_LEFT_AUDIO = 10 // Anda akan membuat layout dan logic untuk ini
        const val MSG_TYPE_RIGHT_AUDIO = 11 // Anda akan membuat layout dan logic untuk ini
        // Tambahkan view type lain sesuai kebutuhan
    }

    override fun getItemViewType(position: Int): Int {
        val message = messageList[position]
        val isSender = message.sender == currentUserId

        return when (message.type) {
            "text" -> if (isSender) MSG_TYPE_RIGHT_TEXT else MSG_TYPE_LEFT_TEXT
            "sound" -> if (isSender) MSG_TYPE_RIGHT_SOUND else MSG_TYPE_LEFT_SOUND
            "image" -> if (isSender) MSG_TYPE_RIGHT_IMAGE else MSG_TYPE_LEFT_IMAGE
            "video" -> if (isSender) MSG_TYPE_RIGHT_VIDEO else MSG_TYPE_LEFT_VIDEO
            "audio" -> if (isSender) MSG_TYPE_RIGHT_AUDIO else MSG_TYPE_LEFT_AUDIO
            "file" -> if (isSender) MSG_TYPE_RIGHT_FILE else MSG_TYPE_LEFT_FILE
            else -> if (isSender) MSG_TYPE_RIGHT_TEXT else MSG_TYPE_LEFT_TEXT // Fallback ke teks
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutInflater = LayoutInflater.from(context)
        val view = when (viewType) {
            MSG_TYPE_RIGHT_TEXT -> layoutInflater.inflate(R.layout.chat_item_right, parent, false)
            MSG_TYPE_LEFT_TEXT -> layoutInflater.inflate(R.layout.chat_item_left, parent, false)
            MSG_TYPE_RIGHT_SOUND -> layoutInflater.inflate(R.layout.chat_item_sound_right, parent, false)
            MSG_TYPE_LEFT_SOUND -> layoutInflater.inflate(R.layout.chat_item_sound_left, parent, false)
            MSG_TYPE_RIGHT_IMAGE -> layoutInflater.inflate(R.layout.item_chat_image_right, parent, false) // BUAT LAYOUT INI
            MSG_TYPE_LEFT_IMAGE -> layoutInflater.inflate(R.layout.item_chat_image_left, parent, false)   // BUAT LAYOUT INI
            MSG_TYPE_RIGHT_VIDEO -> layoutInflater.inflate(R.layout.item_chat_video_right, parent, false)
            MSG_TYPE_LEFT_VIDEO -> layoutInflater.inflate(R.layout.item_chat_video_left, parent, false)
            MSG_TYPE_RIGHT_FILE -> layoutInflater.inflate(R.layout.item_chat_file_right, parent, false)
            MSG_TYPE_LEFT_FILE -> layoutInflater.inflate(R.layout.item_chat_file_left, parent, false)
            MSG_TYPE_RIGHT_AUDIO -> layoutInflater.inflate(R.layout.item_chat_audio_right, parent, false)
            MSG_TYPE_LEFT_AUDIO -> layoutInflater.inflate(R.layout.item_chat_audio_left, parent, false)

            else -> layoutInflater.inflate(R.layout.chat_item_right, parent, false) // Default
        }
        return MessageViewHolder(view)
    }

    override fun getItemCount(): Int {
        return messageList.size
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Common views (mungkin tidak semua ada di setiap layout)
        val showMessage: TextView? = itemView.findViewById(R.id.show_message) // Untuk teks & nama file
        val profileImage: CircleImageView? = itemView.findViewById(R.id.profile_image_chat_item)
        val playSoundButton: ImageView? = itemView.findViewById(R.id.play_sound_message_btn)

        // Views for attachments (tambahkan ID ini ke layout yang sesuai)
        val messageImageView: ImageView? = itemView.findViewById(R.id.message_image_view) // Untuk gambar
        val fileNameTextView: TextView? = itemView.findViewById(R.id.file_name_text_view) // Untuk nama file (jika beda dari showMessage)
        val fileIconImageView: ImageView? = itemView.findViewById(R.id.file_icon_image_view) // Untuk ikon file
        // Tambahkan view lain jika perlu (misal, progress bar download, tombol play video)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messageList[position]
        val viewType = getItemViewType(position)

        // Handle Profile Image (Hanya untuk pesan kiri)
        if (viewType == MSG_TYPE_LEFT_TEXT || viewType == MSG_TYPE_LEFT_SOUND ||
            viewType == MSG_TYPE_LEFT_IMAGE || viewType == MSG_TYPE_LEFT_VIDEO ||
            viewType == MSG_TYPE_LEFT_FILE || viewType == MSG_TYPE_LEFT_AUDIO
        ) {
            holder.profileImage?.visibility = View.VISIBLE
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
            holder.profileImage?.visibility = View.GONE
        }


        // Handle konten berdasarkan tipe pesan
        when (message.type) {
            "text" -> {
                holder.showMessage?.text = message.message
                holder.messageImageView?.visibility = View.GONE
                holder.playSoundButton?.visibility = View.GONE
                holder.fileNameTextView?.visibility = View.GONE
                holder.fileIconImageView?.visibility = View.GONE
            }
            "sound" -> {
                holder.showMessage?.text = message.soundTitle ?: "Play Sound"
                holder.messageImageView?.visibility = View.GONE
                holder.playSoundButton?.visibility = View.VISIBLE
                holder.fileNameTextView?.visibility = View.GONE
                holder.fileIconImageView?.visibility = View.GONE

                holder.playSoundButton?.setOnClickListener {
                    message.fileUrl?.let { url -> playSoundUrl(url) }
                }
            }
            "image" -> {
                holder.showMessage?.visibility = View.GONE // Sembunyikan bubble teks jika hanya gambar
                holder.playSoundButton?.visibility = View.GONE
                holder.fileNameTextView?.visibility = View.GONE // Atau tampilkan nama file di bawah gambar
                holder.fileIconImageView?.visibility = View.GONE

                holder.messageImageView?.visibility = View.VISIBLE
                holder.messageImageView?.let { imageView ->
                    if (!message.fileUrl.isNullOrEmpty()) {
                        Glide.with(context)
                            .load(message.fileUrl)
                            .placeholder(R.drawable.ic_image_placeholder) // GANTI DENGAN PLACEHOLDER ANDA
                            .error(R.drawable.ic_broken_image) // GANTI DENGAN ERROR PLACEHOLDER ANDA
                            .into(imageView)
                        imageView.setOnClickListener {
                            // Implementasi buka gambar fullscreen
                            openMediaUrl(message.fileUrl!!, "image/*")
                        }
                    } else {
                        imageView.setImageResource(R.drawable.ic_broken_image) // Gambar default jika URL null
                    }
                }
            }
            "file" -> {
                holder.showMessage?.visibility = View.GONE // Atau gunakan untuk nama file jika tidak ada fileNameTextView
                holder.messageImageView?.visibility = View.GONE
                holder.playSoundButton?.visibility = View.GONE
                holder.fileIconImageView?.visibility = View.VISIBLE // Tampilkan ikon file
                holder.fileNameTextView?.visibility = View.VISIBLE
                holder.fileNameTextView?.text = message.fileName ?: message.message ?: "File"

                // Atur ikon file berdasarkan ekstensi atau tipe (sederhana)
                holder.fileIconImageView?.setImageResource(getFileIcon(message.fileName))

                holder.itemView.setOnClickListener {
                    message.fileUrl?.let { url ->
                        // Coba dapatkan tipe MIME dari nama file untuk Intent
                        val fileExtension = message.fileName?.substringAfterLast('.', "")?.lowercase()
                        val mimeType = when (fileExtension) {
                            "pdf" -> "application/pdf"
                            "doc", "docx" -> "application/msword"
                            "xls", "xlsx" -> "application/vnd.ms-excel"
                            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
                            "txt" -> "text/plain"
                            "zip" -> "application/zip"
                            "rar" -> "application/x-rar-compressed"
                            // Tambahkan ekstensi umum lainnya
                            else -> "*/*" // Tipe umum jika tidak dikenal
                        }
                        openMediaUrl(url, mimeType)
                    }
                }
            }
            "video" -> {
                // Tampilkan thumbnail video (bisa dari message.fileUrl jika itu gambar thumbnail,
                // atau Anda perlu mekanisme lain). Tampilkan nama file.
                // Tombol play bisa membuka video menggunakan Intent.
                holder.showMessage?.visibility = View.GONE
                holder.playSoundButton?.visibility = View.GONE
                holder.fileIconImageView?.visibility = View.GONE // Atau ikon video
                holder.fileNameTextView?.visibility = View.VISIBLE
                holder.fileNameTextView?.text = message.fileName ?: message.message ?: "Video"

                holder.messageImageView?.visibility = View.VISIBLE // Gunakan ini untuk thumbnail
                holder.messageImageView?.setImageResource(R.drawable.ic_video_placeholder) // Placeholder video
                // Anda mungkin ingin memuat thumbnail video di sini jika tersedia
                // Glide.with(context).load(URL_THUMBNAIL_VIDEO).into(holder.messageImageView)


                holder.itemView.setOnClickListener {
                    message.fileUrl?.let { url ->
                        openMediaUrl(url, "video/*")
                    }
                }
            }
            "audio" -> {
                // Mirip dengan "file", tapi dengan ikon audio dan mungkin kontrol pemutaran audio sederhana.
                holder.showMessage?.visibility = View.GONE
                holder.messageImageView?.visibility = View.GONE
                holder.playSoundButton?.visibility = View.GONE // Atau gunakan tombol play khusus audio
                holder.fileIconImageView?.visibility = View.VISIBLE
                holder.fileNameTextView?.visibility = View.VISIBLE
                holder.fileNameTextView?.text = message.fileName ?: message.message ?: "Audio"

                holder.fileIconImageView?.setImageResource(R.drawable.ic_audio_file) // Placeholder audio

                holder.itemView.setOnClickListener {
                    message.fileUrl?.let { url ->
                        // Anda bisa memutar ini dengan MediaPlayer seperti sound,
                        // atau membukanya dengan intent.
                        openMediaUrl(url, "audio/*")
                        // Atau: playSoundUrl(url) // Jika ingin perilaku seperti sound effect
                    }
                }
            }
            else -> {
                // Fallback untuk tipe tidak dikenal (tampilkan sebagai teks)
                holder.showMessage?.text = message.message ?: "Unsupported message type"
                holder.messageImageView?.visibility = View.GONE
                holder.playSoundButton?.visibility = View.GONE
                holder.fileNameTextView?.visibility = View.GONE
                holder.fileIconImageView?.visibility = View.GONE
            }
        }
    }

    private fun getFileIcon(fileName: String?): Int {
        return when (fileName?.substringAfterLast('.', "")?.lowercase()) {
            "pdf" -> R.drawable.ic_file_pdf
            "doc", "docx" -> R.drawable.ic_file_word
            "xls", "xlsx" -> R.drawable.ic_file_excel
            "ppt", "pptx" -> R.drawable.ic_file_powerpoint
            "txt" -> R.drawable.ic_file_text
            "zip", "rar" -> R.drawable.ic_file_archive
            "mp3", "wav", "ogg", "m4a" -> R.drawable.ic_audio_file // Sama dengan tipe audio
            "mp4", "mkv", "avi", "mov" -> R.drawable.ic_video_placeholder // Sama dengan tipe video
            "jpg", "jpeg", "png", "gif", "bmp" -> R.drawable.ic_image_placeholder // Sama dengan tipe image
            else -> R.drawable.ic_file_generic // Ikon file generik
        }
        // Pastikan Anda memiliki drawable ini di res/drawable Anda.
        // Anda bisa mencari ikon-ikon ini dari Material Design Icons atau sumber lain.
    }


    private fun openMediaUrl(url: String, type: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.parse(url), type)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Penting jika URL adalah content URI (meskipun di sini adalah https)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "No app found to open this file type.", Toast.LENGTH_LONG).show()
                // Mungkin tawarkan untuk mengunduh atau menyalin URL
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No application can handle this request. Please install a web browser or a suitable app.", Toast.LENGTH_LONG).show()
            Log.e("MessageAdapter", "ActivityNotFoundException for URL: $url, Type: $type", e)
        } catch (e: Exception) {
            Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("MessageAdapter", "Error opening URL: $url, Type: $type", e)
        }
    }


    private var chatMediaPlayer: MediaPlayer? = null
    private fun playSoundUrl(url: String) {
        try {
            chatMediaPlayer?.release()
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
    // Ingat untuk melepaskan mediaPlayer saat Activity/Fragment dihancurkan.
    // Anda bisa membuat fungsi public di adapter untuk dipanggil dari Activity:
    fun releaseMediaPlayer() {
        chatMediaPlayer?.release()
        chatMediaPlayer = null
    }
}