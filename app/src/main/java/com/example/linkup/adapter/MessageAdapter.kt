package com.example.linkup.adapter

import android.annotation.SuppressLint
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
    private val messageList: MutableList<ChatMessageModel>,
    private val currentUserId: String,
    private val recipientProfileImageUrl: String?
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private var chatMediaPlayer: MediaPlayer? = null
    private var currentlyPlayingPosition: Int = -1
    private var isAudioPlaying: Boolean = false

    companion object {
        const val MSG_TYPE_LEFT_TEXT = 0
        const val MSG_TYPE_RIGHT_TEXT = 1
        const val MSG_TYPE_LEFT_SOUND = 2
        const val MSG_TYPE_RIGHT_SOUND = 3
        const val MSG_TYPE_LEFT_IMAGE = 4
        const val MSG_TYPE_RIGHT_IMAGE = 5
        const val MSG_TYPE_LEFT_VIDEO = 6
        const val MSG_TYPE_RIGHT_VIDEO = 7
        const val MSG_TYPE_LEFT_FILE = 8
        const val MSG_TYPE_RIGHT_FILE = 9
        const val MSG_TYPE_LEFT_AUDIO = 10
        const val MSG_TYPE_RIGHT_AUDIO = 11

        private const val TAG = "MessageAdapter"
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateMessages(newMessages: List<ChatMessageModel>) {
        this.messageList.clear()
        this.messageList.addAll(newMessages)
        notifyDataSetChanged()
        Log.d(TAG, "Adapter messages updated. New count: ${newMessages.size}")
    }

    override fun getItemViewType(position: Int): Int {
        val message = messageList[position]
        val isSender = message.sender == currentUserId

        // Log.d(TAG, "getItemViewType - Pos: $position, MsgType: ${message.type}, SenderID: ${message.sender}, CurrentUID: $currentUserId, IsSender: $isSender, FileURL: ${message.fileUrl}")

        return when (message.type) {
            "text" -> if (isSender) MSG_TYPE_RIGHT_TEXT else MSG_TYPE_LEFT_TEXT
            "sound" -> if (isSender) MSG_TYPE_RIGHT_SOUND else MSG_TYPE_LEFT_SOUND
            "image" -> if (isSender) MSG_TYPE_RIGHT_IMAGE else MSG_TYPE_LEFT_IMAGE
            "video" -> if (isSender) MSG_TYPE_RIGHT_VIDEO else MSG_TYPE_LEFT_VIDEO
            "audio" -> if (isSender) MSG_TYPE_RIGHT_AUDIO else MSG_TYPE_LEFT_AUDIO
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
            MSG_TYPE_RIGHT_AUDIO -> layoutInflater.inflate(R.layout.item_chat_audio_right, parent, false)
            MSG_TYPE_LEFT_AUDIO -> layoutInflater.inflate(R.layout.item_chat_audio_left, parent, false)
            else -> layoutInflater.inflate(R.layout.chat_item_right, parent, false)
        }
        return MessageViewHolder(view)
    }

    override fun getItemCount(): Int {
        return messageList.size
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val showMessage: TextView? = itemView.findViewById(R.id.show_message)
        val profileImage: CircleImageView? = itemView.findViewById(R.id.profile_image_chat_item)
        val playSoundButton: ImageView? = itemView.findViewById(R.id.play_sound_message_btn)
        val messageImageView: ImageView? = itemView.findViewById(R.id.message_image_view)
        val fileNameTextView: TextView? = itemView.findViewById(R.id.file_name_text_view)
        val fileIconImageView: ImageView? = itemView.findViewById(R.id.file_icon_image_view)
        val videoPlayOverlay: ImageView? = itemView.findViewById(R.id.video_play_overlay)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messageList[position]
        val viewType = getItemViewType(position)

        holder.showMessage?.visibility = View.GONE
        holder.messageImageView?.visibility = View.GONE
        holder.playSoundButton?.visibility = View.GONE
        holder.fileNameTextView?.visibility = View.GONE
        holder.fileIconImageView?.visibility = View.GONE
        holder.videoPlayOverlay?.visibility = View.GONE

        if (viewType !in listOf(MSG_TYPE_RIGHT_TEXT, MSG_TYPE_RIGHT_SOUND, MSG_TYPE_RIGHT_IMAGE,
                MSG_TYPE_RIGHT_VIDEO, MSG_TYPE_RIGHT_FILE, MSG_TYPE_RIGHT_AUDIO)) {
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

        when (message.type) {
            "text" -> {
                holder.showMessage?.visibility = View.VISIBLE
                holder.showMessage?.text = message.message
            }
            "sound", "audio" -> {
                holder.playSoundButton?.visibility = View.VISIBLE
                holder.fileNameTextView?.visibility = View.VISIBLE
                holder.fileNameTextView?.text = message.soundTitle ?: message.fileName ?: if(message.type == "sound") "Voice Note" else "Audio File"
                holder.fileIconImageView?.visibility = if(message.type == "audio") View.VISIBLE else View.GONE
                if(message.type == "audio") holder.fileIconImageView?.setImageResource(R.drawable.ic_audio_file)

                if (position == currentlyPlayingPosition && isAudioPlaying) {
                    holder.playSoundButton?.setImageResource(R.drawable.ic_pause)
                } else {
                    holder.playSoundButton?.setImageResource(R.drawable.ic_play)
                }

                // ===== PENYESUAIAN UNTUK SOUND/AUDIO =====
                holder.playSoundButton?.setOnClickListener {
                    if (!message.fileUrl.isNullOrEmpty()) {
                        handlePlaySound(position, message.fileUrl)
                    } else {
                        Toast.makeText(context, "Audio file not available.", Toast.LENGTH_SHORT).show()
                        Log.w(TAG, "Play sound/audio clicked but fileUrl is null for messageId: ${message.messageId}")
                    }
                }
                if(message.type == "audio"){
                    holder.itemView.setOnClickListener {
                        if (!message.fileUrl.isNullOrEmpty()) {
                            handlePlaySound(position, message.fileUrl)
                        } else {
                            Toast.makeText(context, "Audio file not available.", Toast.LENGTH_SHORT).show()
                            Log.w(TAG, "Audio item clicked but fileUrl is null for messageId: ${message.messageId}")
                        }
                    }
                }
            }
            "image" -> {
                holder.messageImageView?.visibility = View.VISIBLE
                holder.messageImageView?.let { imageView ->
                    // ===== PENYESUAIAN UNTUK IMAGE =====
                    if (!message.fileUrl.isNullOrEmpty()) {
                        Glide.with(context)
                            .load(message.fileUrl)
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_broken_image)
                            .into(imageView)
                        imageView.setOnClickListener {
                            // fileUrl sudah dicek non-null di atas untuk Glide
                            val intent = Intent(context, MediaViewActivity::class.java).apply {
                                putExtra(MediaViewActivity.EXTRA_MEDIA_URL, message.fileUrl)
                                putExtra(MediaViewActivity.EXTRA_MEDIA_TYPE, "image")
                            }
                            context.startActivity(intent)
                        }
                    } else {
                        imageView.setImageResource(R.drawable.ic_broken_image) // Tampilkan gambar rusak jika URL null
                        Log.w(TAG, "Image fileUrl is null for messageId: ${message.messageId}")
                        // Nonaktifkan klik jika tidak ada URL
                        imageView.setOnClickListener(null)
                    }
                }
            }
            "video" -> {
                holder.messageImageView?.visibility = View.VISIBLE
                holder.videoPlayOverlay?.visibility = View.VISIBLE
                holder.fileNameTextView?.visibility = View.VISIBLE
                holder.fileNameTextView?.text = message.fileName ?: "Video"

                // ===== PENYESUAIAN UNTUK VIDEO =====
                if (!message.fileUrl.isNullOrEmpty()) {
                    Glide.with(context)
                        .load(message.fileUrl) // Asumsi fileUrl adalah URL video yang bisa jadi thumbnail
                        .placeholder(R.drawable.ic_video_placeholder)
                        .error(R.drawable.ic_broken_image)
                        .centerCrop()
                        .into(holder.messageImageView!!) // Hati-hati dengan !! jika messageImageView bisa null

                    holder.itemView.setOnClickListener {
                        // fileUrl sudah dicek non-null di atas
                        val intent = Intent(context, MediaViewActivity::class.java).apply {
                            putExtra(MediaViewActivity.EXTRA_MEDIA_URL, message.fileUrl)
                            putExtra(MediaViewActivity.EXTRA_MEDIA_TYPE, "video")
                        }
                        context.startActivity(intent)
                    }
                } else {
                    holder.messageImageView?.setImageResource(R.drawable.ic_video_placeholder) // Placeholder jika URL null
                    Log.w(TAG, "Video fileUrl is null for messageId: ${message.messageId}")
                    // Nonaktifkan klik jika tidak ada URL
                    holder.itemView.setOnClickListener(null)
                    holder.videoPlayOverlay?.visibility = View.GONE // Sembunyikan overlay play jika tidak ada video
                }
            }
            "file" -> {
                holder.fileIconImageView?.visibility = View.VISIBLE
                holder.fileNameTextView?.visibility = View.VISIBLE
                holder.fileNameTextView?.text = message.fileName ?: "File"
                holder.fileIconImageView?.setImageResource(getFileIconResource(message.fileName))

                // ===== PENYESUAIAN UNTUK FILE =====
                if (!message.fileUrl.isNullOrEmpty()) {
                    holder.itemView.setOnClickListener {
                        // fileUrl sudah dicek non-null
                        val fileName = message.fileName ?: "downloaded_file"
                        downloadAndOpenFile(message.fileUrl!!, fileName) // fileUrl di sini aman karena sudah dicek
                    }
                } else {
                    Log.w(TAG, "File fileUrl is null for messageId: ${message.messageId}")
                    Toast.makeText(context, "File not available.", Toast.LENGTH_SHORT).show()
                    // Nonaktifkan klik jika tidak ada URL
                    holder.itemView.setOnClickListener(null)
                }
            }
            else -> {
                holder.showMessage?.visibility = View.VISIBLE
                holder.showMessage?.text = message.message // message adalah non-null di model Anda
            }
        }
    }

    // handlePlaySound sudah cukup baik dalam menangani fileUrl null di awal.
    private fun handlePlaySound(clickedPosition: Int, fileUrl: String?) { // parameter fileUrl sudah nullable
        if (fileUrl.isNullOrEmpty()) { // Pemeriksaan sudah ada di sini
            Toast.makeText(context, "Audio source not found", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "handlePlaySound called with null/empty fileUrl.")
            return
        }

        // ... sisa logika handlePlaySound ...
        if (clickedPosition == currentlyPlayingPosition && isAudioPlaying) {
            chatMediaPlayer?.pause()
            isAudioPlaying = false
            notifyItemChanged(currentlyPlayingPosition)
        } else {
            chatMediaPlayer?.release()
            chatMediaPlayer = null
            if(isAudioPlaying) {
                val oldPlayingPosition = currentlyPlayingPosition
                isAudioPlaying = false
                notifyItemChanged(oldPlayingPosition)
            }
            currentlyPlayingPosition = clickedPosition
            isAudioPlaying = true
            playSoundUrlInternal(fileUrl, clickedPosition) // fileUrl diteruskan
            notifyItemChanged(clickedPosition)
        }
    }


    private fun playSoundUrlInternal(url: String, position: Int) { // url di sini diharapkan non-null karena sudah dicek sebelumnya
        try {
            chatMediaPlayer = MediaPlayer().apply {
                setDataSource(url) // url tidak akan null di sini jika alurnya benar
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
            else -> R.drawable.ic_file_generic
        }
    }

    // downloadAndOpenFile sudah menerima fileUrl sebagai non-null, yang baik.
    // Pastikan pemanggilannya selalu memberikan URL yang valid.
    private fun downloadAndOpenFile(fileUrl: String, fileName: String) {
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            fileName.substringAfterLast('.', "").lowercase()
        ) ?: "*/*"

        try {
            val request = DownloadManager.Request(Uri.parse(fileUrl)) // fileUrl tidak akan null di sini
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

        } catch (e: Exception) {
            Log.e(TAG, "Error starting download for $fileUrl: ${e.localizedMessage}", e)
            Toast.makeText(context, "Failed to start download. Trying to open directly...", Toast.LENGTH_LONG).show()
            openExternalApp(fileUrl, mimeType) // fileUrl diteruskan
        }
    }


    private fun openExternalApp(url: String, type: String) { // url di sini diharapkan non-null
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), type) // url tidak akan null di sini
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
            notifyItemChanged(currentlyPlayingPosition)
            currentlyPlayingPosition = -1
        }
        Log.d(TAG, "MediaPlayer released")
    }
}