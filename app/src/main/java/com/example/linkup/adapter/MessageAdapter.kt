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
import com.example.linkup.MediaViewActivity
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
        // Log.d(TAG, "Adapter messages updated. New count: ${newMessages.size}") // Optional logging
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
            else -> {
                Log.w(TAG, "Unknown message type: ${message.type} at pos $position. Defaulting to text.")
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
            else -> layoutInflater.inflate(R.layout.chat_item_right, parent, false) // Default
        }
        return MessageViewHolder(view)
    }

    override fun getItemCount(): Int = messageList.size

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val showMessage: TextView? = itemView.findViewById(R.id.show_message)
        val profileImage: CircleImageView? = itemView.findViewById(R.id.profile_image_chat_item)
        val soundTitleTextView: TextView? = itemView.findViewById(R.id.sound_title)
        val playSoundButton: ImageView? = itemView.findViewById(R.id.play_sound_message_btn)
        val messageImageView: ImageView? = itemView.findViewById(R.id.message_image_view)
        val fileNameTextView: TextView? = itemView.findViewById(R.id.file_name_text_view)
        val fileIconImageView: ImageView? = itemView.findViewById(R.id.file_icon_image_view)
        val videoPlayOverlay: ImageView? = itemView.findViewById(R.id.video_play_overlay)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messageList[position]
        val viewType = getItemViewType(position)

        // Reset visibility for all potentially used views
        holder.showMessage?.visibility = View.GONE
        holder.soundTitleTextView?.visibility = View.GONE
        holder.fileNameTextView?.visibility = View.GONE
        holder.messageImageView?.visibility = View.GONE
        holder.playSoundButton?.visibility = View.GONE
        holder.fileIconImageView?.visibility = View.GONE
        holder.videoPlayOverlay?.visibility = View.GONE
        holder.profileImage?.visibility = View.GONE // Reset profile image too

        // Profile Image Logic
        if (viewType !in listOf(
                MSG_TYPE_RIGHT_TEXT, MSG_TYPE_RIGHT_SOUND, MSG_TYPE_RIGHT_IMAGE,
                MSG_TYPE_RIGHT_VIDEO, MSG_TYPE_RIGHT_FILE, MSG_TYPE_RIGHT_AUDIO
            )
        ) {
            holder.profileImage?.visibility = View.VISIBLE
            holder.profileImage?.let {
                if (!recipientProfileImageUrl.isNullOrEmpty()) {
                    Glide.with(context).load(recipientProfileImageUrl)
                        .placeholder(R.drawable.profile).error(R.drawable.profile).into(it)
                } else {
                    it.setImageResource(R.drawable.profile)
                }
            }
        }

        // Bind data based on message type
        when (message.type) {
            "text" -> {
                holder.showMessage?.visibility = View.VISIBLE
                holder.showMessage?.text = message.message
            }
            "sound" -> { // Voice Note
                holder.soundTitleTextView?.visibility = View.VISIBLE
                holder.playSoundButton?.visibility = View.VISIBLE
                holder.soundTitleTextView?.text = message.soundTitle ?: "Voice Note"

                updatePlayPauseButtonState(holder, position)
                holder.playSoundButton?.setOnClickListener {
                    handlePlaySoundClick(position, message.fileUrl, message.messageId)
                }
            }
            "audio" -> { // General Audio File
                holder.fileNameTextView?.visibility = View.VISIBLE
                holder.playSoundButton?.visibility = View.VISIBLE
                holder.fileIconImageView?.visibility = View.VISIBLE

                holder.fileNameTextView?.text = message.fileName ?: message.soundTitle ?: "Audio File"
                holder.fileIconImageView?.setImageResource(R.drawable.ic_audio_file)

                updatePlayPauseButtonState(holder, position)
                val playClickListener = View.OnClickListener {
                    handlePlaySoundClick(position, message.fileUrl, message.messageId)
                }
                holder.playSoundButton?.setOnClickListener(playClickListener)
                holder.itemView.setOnClickListener(playClickListener) // Allow click on whole item
            }
            "image" -> {
                holder.messageImageView?.visibility = View.VISIBLE
                holder.messageImageView?.let { imageView ->
                    if (!message.fileUrl.isNullOrEmpty()) {
                        Glide.with(context).load(message.fileUrl)
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_broken_image).into(imageView)
                        imageView.setOnClickListener {
                            val intent = Intent(context, MediaViewActivity::class.java).apply {
                                putExtra(MediaViewActivity.EXTRA_MEDIA_URL, message.fileUrl)
                                putExtra(MediaViewActivity.EXTRA_MEDIA_TYPE, "image")
                            }
                            context.startActivity(intent)
                        }
                    } else {
                        imageView.setImageResource(R.drawable.ic_broken_image)
                        Log.w(TAG, "Image fileUrl is null for messageId: ${message.messageId}")
                        imageView.setOnClickListener(null)
                    }
                }
            }
            "video" -> {
                holder.messageImageView?.visibility = View.VISIBLE // For thumbnail
                holder.videoPlayOverlay?.visibility = View.VISIBLE
                holder.fileNameTextView?.visibility = View.VISIBLE
                holder.fileNameTextView?.text = message.fileName ?: "Video"

                if (!message.fileUrl.isNullOrEmpty()) {
                    Glide.with(context).load(message.fileUrl) // Thumbnail
                        .placeholder(R.drawable.ic_video_placeholder)
                        .error(R.drawable.ic_broken_image).centerCrop()
                        .into(holder.messageImageView!!) // Use with caution if messageImageView can be from a different layout type

                    holder.itemView.setOnClickListener {
                        val intent = Intent(context, MediaViewActivity::class.java).apply {
                            putExtra(MediaViewActivity.EXTRA_MEDIA_URL, message.fileUrl)
                            putExtra(MediaViewActivity.EXTRA_MEDIA_TYPE, "video")
                        }
                        context.startActivity(intent)
                    }
                } else {
                    holder.messageImageView?.setImageResource(R.drawable.ic_video_placeholder)
                    Log.w(TAG, "Video fileUrl is null for messageId: ${message.messageId}")
                    holder.itemView.setOnClickListener(null)
                    holder.videoPlayOverlay?.visibility = View.GONE
                }
            }
            "file" -> {
                holder.fileIconImageView?.visibility = View.VISIBLE
                holder.fileNameTextView?.visibility = View.VISIBLE
                holder.fileNameTextView?.text = message.fileName ?: "File"
                holder.fileIconImageView?.setImageResource(getFileIconResource(message.fileName))

                if (!message.fileUrl.isNullOrEmpty()) {
                    holder.itemView.setOnClickListener {
                        val fileName = message.fileName ?: "downloaded_file_${System.currentTimeMillis()}"
                        downloadAndOpenFile(message.fileUrl!!, fileName)
                    }
                } else {
                    Log.w(TAG, "File fileUrl is null for messageId: ${message.messageId}")
                    Toast.makeText(context, "File not available.", Toast.LENGTH_SHORT).show()
                    holder.itemView.setOnClickListener(null)
                }
            }
            else -> {
                holder.showMessage?.visibility = View.VISIBLE
                holder.showMessage?.text = message.message // Or "Unsupported message type"
                Log.w(TAG, "Unhandled message type: ${message.type} at pos $position.")
            }
        }
    }

    private fun updatePlayPauseButtonState(holder: MessageViewHolder, position: Int) {
        if (position == currentlyPlayingPosition && isAudioPlaying) {
            holder.playSoundButton?.setImageResource(R.drawable.ic_pause)
        } else {
            holder.playSoundButton?.setImageResource(R.drawable.ic_play)
        }
    }

    private fun handlePlaySoundClick(position: Int, fileUrl: String?, messageId: String?) {
        if (!fileUrl.isNullOrEmpty()) {
            handlePlaySound(position, fileUrl) // Pass non-null fileUrl
        } else {
            Toast.makeText(context, "Audio file not available.", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "Play sound/audio clicked but fileUrl is null for messageId: $messageId")
        }
    }

    private fun handlePlaySound(clickedPosition: Int, fileUrl: String) { // fileUrl is non-null here
        if (clickedPosition == currentlyPlayingPosition && isAudioPlaying) {
            chatMediaPlayer?.pause()
            isAudioPlaying = false
            notifyItemChanged(currentlyPlayingPosition) // Update only the affected item
        } else {
            chatMediaPlayer?.release() // Release previous player if any
            chatMediaPlayer = null

            if (isAudioPlaying) { // If another audio was playing, update its UI
                val oldPlayingPosition = currentlyPlayingPosition
                isAudioPlaying = false
                if (oldPlayingPosition != -1) notifyItemChanged(oldPlayingPosition)
            }

            currentlyPlayingPosition = clickedPosition
            isAudioPlaying = true
            playSoundUrlInternal(fileUrl, clickedPosition)
            notifyItemChanged(clickedPosition) // Update the newly playing item
        }
    }

    private fun playSoundUrlInternal(url: String, position: Int) {
        try {
            chatMediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener { start() }
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
                    Log.e(TAG, "MediaPlayer Error for URL: $url")
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
            Log.e(TAG, "Error setting up MediaPlayer for URL: $url", e)
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

        } catch (e: Exception) {
            Log.e(TAG, "Error starting download for $fileUrl: ${e.localizedMessage}", e)
            Toast.makeText(context, "Download failed. Trying to open directly...", Toast.LENGTH_LONG).show()
            openExternalApp(fileUrl, mimeType)
        }
    }

    private fun openExternalApp(url: String, type: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), type)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Needed if starting from non-Activity context
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "No app found to open this file type.", Toast.LENGTH_LONG).show()
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No application can handle this request.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "ActivityNotFoundException for URL: $url, Type: $type", e)
        } catch (e: Exception) { // Catch more general exceptions
            Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error opening URL: $url, Type: $type", e)
        }
    }

    fun releaseMediaPlayer() {
        chatMediaPlayer?.release()
        chatMediaPlayer = null
        if (isAudioPlaying && currentlyPlayingPosition != -1) {
            isAudioPlaying = false // Ensure state is reset
            notifyItemChanged(currentlyPlayingPosition)
        }
        currentlyPlayingPosition = -1
        // Log.d(TAG, "MediaPlayer released") // Optional logging
    }
}