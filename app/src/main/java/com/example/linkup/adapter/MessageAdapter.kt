package com.example.linkup.adapter

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.linkup.MediaViewActivity // PASTIKAN ANDA MEMBUAT ACTIVITY INI
import com.example.linkup.R
import com.example.linkup.model.ChatMessageModel
import de.hdodenhof.circleimageview.CircleImageView

class MessageAdapter(
    private val context: Context,
    private val messageList: MutableList<ChatMessageModel>, // Ubah ke MutableList jika ingin modifikasi
    private val currentUserId: String,
    private val recipientProfileImageUrl: String?
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private var chatMediaPlayer: MediaPlayer? = null
    private var currentlyPlayingPosition: Int = -1
    private var isAudioPlaying: Boolean = false

    companion object {
        const val MSG_TYPE_LEFT_TEXT = 0
        const val MSG_TYPE_RIGHT_TEXT = 1
        const val MSG_TYPE_LEFT_SOUND = 2 // Untuk voice note/sound effect singkat
        const val MSG_TYPE_RIGHT_SOUND = 3
        const val MSG_TYPE_LEFT_IMAGE = 4
        const val MSG_TYPE_RIGHT_IMAGE = 5
        const val MSG_TYPE_LEFT_VIDEO = 6
        const val MSG_TYPE_RIGHT_VIDEO = 7
        const val MSG_TYPE_LEFT_FILE = 8  // Untuk dokumen, pdf, dll.
        const val MSG_TYPE_RIGHT_FILE = 9
        const val MSG_TYPE_LEFT_AUDIO = 10 // Untuk file audio panjang (musik, rekaman)
        const val MSG_TYPE_RIGHT_AUDIO = 11

        private const val TAG = "MessageAdapter"
    }

    override fun getItemViewType(position: Int): Int {
        val message = messageList[position]
        val isSender = message.sender == currentUserId

        Log.d(TAG, "getItemViewType - Pos: $position, MsgType: ${message.type}, SenderID: ${message.sender}, CurrentUID: $currentUserId, IsSender: $isSender, FileURL: ${message.fileUrl}")

        return when (message.type) {
            "text" -> if (isSender) MSG_TYPE_RIGHT_TEXT else MSG_TYPE_LEFT_TEXT
            "sound" -> if (isSender) MSG_TYPE_RIGHT_SOUND else MSG_TYPE_LEFT_SOUND // Misal, voice note singkat
            "image" -> if (isSender) MSG_TYPE_RIGHT_IMAGE else MSG_TYPE_LEFT_IMAGE
            "video" -> if (isSender) MSG_TYPE_RIGHT_VIDEO else MSG_TYPE_LEFT_VIDEO
            "audio" -> if (isSender) MSG_TYPE_RIGHT_AUDIO else MSG_TYPE_LEFT_AUDIO // Misal, file musik
            "file" -> if (isSender) MSG_TYPE_RIGHT_FILE else MSG_TYPE_LEFT_FILE
            else -> {
                Log.w(TAG, "Unknown message type: ${message.type} at position $position. Defaulting to text.")
                if (isSender) MSG_TYPE_RIGHT_TEXT else MSG_TYPE_LEFT_TEXT
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutInflater = LayoutInflater.from(context)
        val view = when (viewType) {
            MSG_TYPE_RIGHT_TEXT -> layoutInflater.inflate(R.layout.chat_item_right, parent, false)
            MSG_TYPE_LEFT_TEXT -> layoutInflater.inflate(R.layout.chat_item_left, parent, false)

            MSG_TYPE_RIGHT_SOUND -> layoutInflater.inflate(R.layout.chat_item_sound_right, parent, false)
            MSG_TYPE_LEFT_SOUND -> layoutInflater.inflate(R.layout.chat_item_sound_left, parent, false)

            MSG_TYPE_RIGHT_IMAGE -> layoutInflater.inflate(R.layout.item_chat_image_right, parent, false)
            MSG_TYPE_LEFT_IMAGE -> layoutInflater.inflate(R.layout.item_chat_image_left, parent, false)

            MSG_TYPE_RIGHT_VIDEO -> layoutInflater.inflate(R.layout.item_chat_video_right, parent, false)
            MSG_TYPE_LEFT_VIDEO -> layoutInflater.inflate(R.layout.item_chat_video_left, parent, false)

            MSG_TYPE_RIGHT_FILE -> layoutInflater.inflate(R.layout.item_chat_file_right, parent, false)
            MSG_TYPE_LEFT_FILE -> layoutInflater.inflate(R.layout.item_chat_file_left, parent, false)

            MSG_TYPE_RIGHT_AUDIO -> layoutInflater.inflate(R.layout.item_chat_audio_right, parent, false) // Bisa sama dengan layout sound atau file
            MSG_TYPE_LEFT_AUDIO -> layoutInflater.inflate(R.layout.item_chat_audio_left, parent, false)  // Bisa sama dengan layout sound atau file

            else -> layoutInflater.inflate(R.layout.chat_item_right, parent, false) // Fallback
        }
        return MessageViewHolder(view)
    }

    override fun getItemCount(): Int {
        return messageList.size
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val showMessage: TextView? = itemView.findViewById(R.id.show_message)
        val profileImage: CircleImageView? = itemView.findViewById(R.id.profile_image_chat_item)
        val playSoundButton: ImageView? = itemView.findViewById(R.id.play_sound_message_btn) // Untuk sound/audio
        val messageImageView: ImageView? = itemView.findViewById(R.id.message_image_view) // Untuk gambar/thumbnail video
        val fileNameTextView: TextView? = itemView.findViewById(R.id.file_name_text_view)
        val fileIconImageView: ImageView? = itemView.findViewById(R.id.file_icon_image_view)
        // Tambahkan view lain jika perlu (misal, untuk video play overlay di thumbnail)
        val videoPlayOverlay: ImageView? = itemView.findViewById(R.id.video_play_overlay) // Contoh ID untuk overlay video
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messageList[position]
        val viewType = getItemViewType(position)

        // Reset visibilitas default
        holder.showMessage?.visibility = View.GONE
        holder.messageImageView?.visibility = View.GONE
        holder.playSoundButton?.visibility = View.GONE
        holder.fileNameTextView?.visibility = View.GONE
        holder.fileIconImageView?.visibility = View.GONE
        holder.videoPlayOverlay?.visibility = View.GONE

        // Handle Profile Image (Hanya untuk pesan kiri)
        if (viewType !in listOf(MSG_TYPE_RIGHT_TEXT, MSG_TYPE_RIGHT_SOUND, MSG_TYPE_RIGHT_IMAGE,
                MSG_TYPE_RIGHT_VIDEO, MSG_TYPE_RIGHT_FILE, MSG_TYPE_RIGHT_AUDIO)) {
            holder.profileImage?.visibility = View.VISIBLE
            holder.profileImage?.let {
                if (!recipientProfileImageUrl.isNullOrEmpty()) {
                    Glide.with(context)
                        .load(recipientProfileImageUrl)
                        .placeholder(R.drawable.profile) // Ganti dengan placeholder Anda
                        .error(R.drawable.profile) // Ganti dengan error placeholder Anda
                        .into(it)
                } else {
                    it.setImageResource(R.drawable.profile) // Ganti dengan default profile Anda
                }
            }
        } else {
            holder.profileImage?.visibility = View.GONE
        }

        when (message.type) {
            "text" -> {
                holder.showMessage?.visibility = View.VISIBLE
                holder.showMessage?.text = message.message
            }
            "sound", "audio" -> { // Menggabungkan logika untuk sound dan audio jika UI nya mirip
                holder.playSoundButton?.visibility = View.VISIBLE
                holder.fileNameTextView?.visibility = View.VISIBLE // Bisa untuk judul sound/nama file audio
                holder.fileNameTextView?.text = message.soundTitle ?: message.fileName ?: if(message.type == "sound") "Voice Note" else "Audio File"
                holder.fileIconImageView?.visibility = if(message.type == "audio") View.VISIBLE else View.GONE // Ikon untuk audio file, bukan untuk sound
                if(message.type == "audio") holder.fileIconImageView?.setImageResource(R.drawable.ic_audio_file)


                if (position == currentlyPlayingPosition && isAudioPlaying) {
                    holder.playSoundButton?.setImageResource(R.drawable.ic_pause) // Ganti dengan ikon pause Anda
                } else {
                    holder.playSoundButton?.setImageResource(R.drawable.ic_play) // Ganti dengan ikon play Anda
                }

                holder.playSoundButton?.setOnClickListener {
                    handlePlaySound(position, message.fileUrl)
                }
                // Jika ingin itemnya sendiri bisa di klik (misal untuk audio file, bukan voice note)
                if(message.type == "audio"){
                    holder.itemView.setOnClickListener {
                        handlePlaySound(position, message.fileUrl)
                    }
                }
            }
            "image" -> {
                holder.messageImageView?.visibility = View.VISIBLE
                holder.messageImageView?.let { imageView ->
                    if (!message.fileUrl.isNullOrEmpty()) {
                        Glide.with(context)
                            .load(message.fileUrl)
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_broken_image)
                            .into(imageView)
                        imageView.setOnClickListener {
                            val intent = Intent(context, MediaViewActivity::class.java).apply {
                                putExtra(MediaViewActivity.EXTRA_MEDIA_URL, message.fileUrl)
                                putExtra(MediaViewActivity.EXTRA_MEDIA_TYPE, "image")
                            }
                            context.startActivity(intent)
                        }
                    } else {
                        imageView.setImageResource(R.drawable.ic_broken_image)
                    }
                }
            }
            "video" -> {
                holder.messageImageView?.visibility = View.VISIBLE // Untuk thumbnail
                holder.videoPlayOverlay?.visibility = View.VISIBLE // Tampilkan ikon play di atas thumbnail
                holder.fileNameTextView?.visibility = View.VISIBLE
                holder.fileNameTextView?.text = message.fileName ?: "Video"

                holder.messageImageView?.setImageResource(R.drawable.ic_video_placeholder) // Placeholder default
                if (!message.fileUrl.isNullOrEmpty()) { // Anda bisa memuat thumbnail video di sini jika ada URL thumbnail terpisah
                    Glide.with(context)
                        .load(message.fileUrl) // Jika fileUrl adalah thumbnail, atau
                        // .load(message.thumbnailUrl) // Jika ada field thumbnailUrl
                        .placeholder(R.drawable.ic_video_placeholder)
                        .error(R.drawable.ic_broken_image)
                        .centerCrop()
                        .into(holder.messageImageView!!)
                }

                holder.itemView.setOnClickListener { // Atau pada messageImageView/videoPlayOverlay
                    message.fileUrl?.let { url ->
                        val intent = Intent(context, MediaViewActivity::class.java).apply {
                            putExtra(MediaViewActivity.EXTRA_MEDIA_URL, url)
                            putExtra(MediaViewActivity.EXTRA_MEDIA_TYPE, "video")
                        }
                        context.startActivity(intent)
                    }
                }
            }
            "file" -> {
                holder.fileIconImageView?.visibility = View.VISIBLE
                holder.fileNameTextView?.visibility = View.VISIBLE
                holder.fileNameTextView?.text = message.fileName ?: "File"
                holder.fileIconImageView?.setImageResource(getFileIconResource(message.fileName))

                holder.itemView.setOnClickListener {
                    message.fileUrl?.let { url ->
                        val fileName = message.fileName ?: "downloaded_file"
                        downloadAndOpenFile(url, fileName)
                    }
                }
            }
            else -> {
                holder.showMessage?.visibility = View.VISIBLE
                holder.showMessage?.text = message.message ?: "Unsupported message"
            }
        }
    }

    private fun handlePlaySound(clickedPosition: Int, fileUrl: String?) {
        if (fileUrl.isNullOrEmpty()) {
            Toast.makeText(context, "Audio source not found", Toast.LENGTH_SHORT).show()
            return
        }

        if (clickedPosition == currentlyPlayingPosition && isAudioPlaying) {
            // Sedang diputar di item yang sama, jadi hentikan
            chatMediaPlayer?.pause() // Atau stop() jika ingin reset
            isAudioPlaying = false // Asumsi pause, ikon akan berubah di notify
            // Untuk benar-benar menghentikan dan mereset:
            // chatMediaPlayer?.stop()
            // chatMediaPlayer?.release()
            // chatMediaPlayer = null
            // isAudioPlaying = false
            notifyItemChanged(currentlyPlayingPosition) // Update ikon tombol
            // currentlyPlayingPosition = -1; // Jika stop total
        } else {
            // Hentikan yang lama jika ada dan berbeda, atau jika tidak ada yang diputar
            chatMediaPlayer?.release()
            chatMediaPlayer = null
            if(isAudioPlaying) { // Jika ada yang sedang diputar di posisi lain
                val oldPlayingPosition = currentlyPlayingPosition
                isAudioPlaying = false // Set dulu agar ikon lama kembali
                notifyItemChanged(oldPlayingPosition)
            }

            // Mulai yang baru
            currentlyPlayingPosition = clickedPosition
            isAudioPlaying = true
            playSoundUrlInternal(fileUrl, clickedPosition)
            notifyItemChanged(clickedPosition) // Update ikon tombol yang baru diklik
        }
    }


    private fun playSoundUrlInternal(url: String, position: Int) {
        try {
            chatMediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener {
                    start()
                }
                setOnCompletionListener { mp ->
                    mp.release()
                    if (position == currentlyPlayingPosition) {
                        isAudioPlaying = false
                        notifyItemChanged(currentlyPlayingPosition)
                        currentlyPlayingPosition = -1
                        chatMediaPlayer = null
                    }
                }
                setOnErrorListener { mp, _, _ ->
                    Toast.makeText(context, "Cannot play audio", Toast.LENGTH_SHORT).show()
                    mp.release()
                    if (position == currentlyPlayingPosition) {
                        isAudioPlaying = false
                        notifyItemChanged(currentlyPlayingPosition)
                        currentlyPlayingPosition = -1
                        chatMediaPlayer = null
                    }
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error playing audio: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error playing sound URL: $url", e)
            if (position == currentlyPlayingPosition) {
                isAudioPlaying = false
                notifyItemChanged(currentlyPlayingPosition)
                currentlyPlayingPosition = -1
                chatMediaPlayer = null
            }
        }
    }

    private fun getFileIconResource(fileName: String?): Int {
        return when (fileName?.substringAfterLast('.', "")?.lowercase()) {
            "pdf" -> R.drawable.ic_file_pdf
            "doc", "docx" -> R.drawable.ic_file_word
            "xls", "xlsx" -> R.drawable.ic_file_excel
            "ppt", "pptx" -> R.drawable.ic_file_powerpoint
            "txt" -> R.drawable.ic_file_text
            "zip", "rar" -> R.drawable.ic_file_archive
            // "mp3", "wav", "ogg", "m4a" -> R.drawable.ic_audio_file // Sudah ditangani oleh tipe "audio"
            // "mp4", "mkv", "avi", "mov" -> R.drawable.ic_video_placeholder // Sudah ditangani oleh tipe "video"
            // "jpg", "jpeg", "png", "gif", "bmp" -> R.drawable.ic_image_placeholder // Sudah ditangani oleh tipe "image"
            else -> R.drawable.ic_file_generic
        }
    }

    private fun downloadAndOpenFile(fileUrl: String, fileName: String) {
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            fileName.substringAfterLast('.', "").lowercase()
        ) ?: "*/*"

        try {
            val request = DownloadManager.Request(Uri.parse(fileUrl))
                .setTitle(fileName)
                .setDescription("Downloading file...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setMimeType(mimeType)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            Toast.makeText(context, "Starting download: $fileName", Toast.LENGTH_LONG).show()

            // Untuk membuka otomatis setelah selesai, Anda perlu BroadcastReceiver yang mendengarkan
            // DownloadManager.ACTION_DOWNLOAD_COMPLETE, lalu dapatkan URI file yang diunduh
            // dan buka dengan Intent.ACTION_VIEW + FileProvider.
            // Untuk sekarang, pengguna bisa membuka dari notifikasi.

        } catch (e: Exception) {
            Log.e(TAG, "Error starting download: ${e.localizedMessage}", e)
            Toast.makeText(context, "Failed to start download. Trying to open directly...", Toast.LENGTH_LONG).show()
            // Fallback: Coba buka langsung jika download manager gagal (mungkin browser bisa handle)
            openExternalApp(fileUrl, mimeType)
        }
    }


    private fun openExternalApp(url: String, type: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), type)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Penting untuk content URIs
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Diperlukan jika context bukan Activity
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "No app found to open this file type.", Toast.LENGTH_LONG).show()
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No application can handle this request.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "ActivityNotFoundException for URL: $url, Type: $type", e)
        } catch (e: Exception) {
            Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error opening URL: $url, Type: $type", e)
        }
    }

    fun releaseMediaPlayer() {
        chatMediaPlayer?.release()
        chatMediaPlayer = null
        isAudioPlaying = false
        if (currentlyPlayingPosition != -1) {
            notifyItemChanged(currentlyPlayingPosition) // Reset ikon tombol yang mungkin masih 'pause'
            currentlyPlayingPosition = -1
        }
        Log.d(TAG, "MediaPlayer released")
    }
}