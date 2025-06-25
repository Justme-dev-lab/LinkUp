package com.example.linkup.ui.message

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels // Untuk by viewModels()
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer // Gunakan Observer standar atau EventObserver
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.linkup.adapter.MessageAdapter
import com.example.linkup.databinding.ActivityMessageChatBinding
import com.example.linkup.model.ChatMessageModel
import com.example.linkup.model.SoundItem
import com.example.linkup.ui.chats.SoundPickerBottomSheetFragment
import com.example.linkup.ui.chats.SoundSelectionListener
import com.example.linkup.utils.EventObserver // Import EventObserver Anda
import com.example.linkup.ui.message.MessageChatViewModel // Import ViewModel Anda
import com.google.firebase.auth.FirebaseAuth
import com.example.linkup.R


class MessageChatActivity : AppCompatActivity(), SoundSelectionListener {

    private lateinit var binding: ActivityMessageChatBinding
    private val viewModel: MessageChatViewModel by viewModels() // Cara mudah inisialisasi ViewModel

    private var currentUserId: String? = null
    private var recipientUserIdFromIntent: String? = null // Ganti nama agar tidak konflik dengan ViewModel
    private var recipientUserName: String? = null
    private var recipientProfileImageUrl: String? = null

    private lateinit var messageAdapter: MessageAdapter

    private val attachmentPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { fileUri ->
                    val mimeType = contentResolver.getType(fileUri)
                    Log.d(TAG, "File selected: ${fileUri.path}, MIME type: $mimeType")

                    val (storageFolder, messageType) = when {
                        mimeType?.startsWith("image/") == true -> "images" to "image"
                        mimeType?.startsWith("video/") == true -> "videos" to "video"
                        mimeType?.startsWith("audio/") == true -> "audios" to "audio"
                        else -> "files" to "file"
                    }
                    val originalFileName = getFileName(fileUri) ?: "attachment"
                    viewModel.uploadFileToStorage(fileUri, storageFolder, messageType, originalFileName)
                }
            } else {
                Log.d(TAG, "Attachment selection cancelled or failed.")
            }
        }

    companion object {
        const val EXTRA_USER_ID = "USER_ID"
        const val EXTRA_USER_NAME = "USER_NAME"
        const val EXTRA_PROFILE_IMAGE_URL = "PROFILE_IMAGE_URL"
        private const val TAG = "MessageChatActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessageChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        recipientUserIdFromIntent = intent.getStringExtra(EXTRA_USER_ID)
        recipientUserName = intent.getStringExtra(EXTRA_USER_NAME)
        recipientProfileImageUrl = intent.getStringExtra(EXTRA_PROFILE_IMAGE_URL)

        if (recipientUserIdFromIntent == null || currentUserId == null) {
            Toast.makeText(this, "Error: User data missing.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Recipient User ID or Current User ID is null. Finishing activity.")
            finish()
            return
        }

        // Setup ViewModel dengan recipientId
        viewModel.setupChat(recipientUserIdFromIntent!!)

        setupToolbar()
        setupRecyclerView()
        observeViewModel() // Panggil observeViewModel setelah setup UI dasar

        binding.sendMessageBtn.setOnClickListener {
            val messageText: String = binding.textMessage.text.toString().trim()
            if (messageText.isEmpty()) {
                Toast.makeText(this@MessageChatActivity, "Please write a message", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.sendMessage(
                    messageText = messageText,
                    messageType = "text"
                )
                binding.textMessage.setText("") // Kosongkan setelah dikirim ke ViewModel
            }
        }

        binding.attachImageFileBtn.setOnClickListener {
            openAttachmentPicker()
        }

        try {
            binding.soundBtnChat.setOnClickListener {
                showSoundPickerBottomSheet()
            }
        } catch (e: NullPointerException) {
            Log.e(TAG, "Sound button (soundBtnChat) not found.", e)
        }
    }

    override fun onResume() {
        super.onResume()
        // Panggil fungsi ViewModel untuk menandai pesan sebagai sudah dibaca
        viewModel.markChatMessagesAsReadOnResume()
    }


    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarChat)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarChat.setNavigationOnClickListener { finish() }
        binding.usernameToolbarChat.text = recipientUserName ?: "User"
        if (!recipientProfileImageUrl.isNullOrEmpty()) {
            Glide.with(this).load(recipientProfileImageUrl).placeholder(R.drawable.profile).into(binding.profileImageToolbarChat)
        } else {
            binding.profileImageToolbarChat.setImageResource(R.drawable.profile)
        }
    }

    private fun setupRecyclerView() {
        // Berikan currentUserId dan recipientProfileImageUrl ke adapter
        // currentUserId diperlukan untuk membedakan pesan kiri/kanan
        // recipientProfileImageUrl untuk menampilkan gambar profil lawan bicara
        messageAdapter = MessageAdapter(this, mutableListOf(), currentUserId!!, recipientProfileImageUrl)
        binding.recycleViewChats.apply {
            setHasFixedSize(true)
            val linearLayoutManager = LinearLayoutManager(this@MessageChatActivity)
            linearLayoutManager.stackFromEnd = true
            layoutManager = linearLayoutManager
            adapter = messageAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.messagesList.observe(this, Observer { messages ->
            Log.d(TAG, "Messages updated in Activity: ${messages.size}")
            messageAdapter.updateMessages(messages) // Pastikan method ini ada di adapter Anda
            if (messages.isNotEmpty()) {
                binding.recycleViewChats.scrollToPosition(messages.size - 1)
            }
        })

        viewModel.messageSentStatus.observe(this, EventObserver { success ->
            if (!success) {
                Toast.makeText(this, "Failed to send message.", Toast.LENGTH_SHORT).show()
            }
            // Tidak perlu membersihkan input text di sini lagi jika sudah dilakukan saat klik tombol
        })

        viewModel.isLoading.observe(this, Observer { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            // Anda mungkin ingin menonaktifkan tombol kirim saat loading
            binding.sendMessageBtn.isEnabled = !isLoading
            binding.attachImageFileBtn.isEnabled = !isLoading
        })

        viewModel.fileUploadProgress.observe(this, EventObserver { progress ->
            Log.d(TAG, "Upload progress: $progress%")
            binding.uploadProgressBar.visibility = View.VISIBLE // Pastikan ada ProgressBar di layout
            binding.uploadProgressBar.progress = progress
            if (progress == 100) {
                binding.uploadProgressBar.visibility = View.GONE // Sembunyikan jika sudah selesai
            }
        })

        viewModel.fileUploadStatus.observe(this, EventObserver { (success, errorMessage) ->
            binding.uploadProgressBar.visibility = View.GONE // Sembunyikan progress bar setelah selesai/gagal
            if (success) {
                Toast.makeText(this, "File uploaded and message sent.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Upload failed: ${errorMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
            }
        })
    }


    private fun openAttachmentPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            attachmentPickerLauncher.launch(Intent.createChooser(intent, "Select a File"))
        } catch (ex: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "Please install a File Manager.", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("Range")
    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1 && cut != null) {
                result = result.substring(cut + 1)
            }
        }
        return result
    }

    private fun showSoundPickerBottomSheet() {
        if (currentUserId == null) {
            Toast.makeText(this, "You need to be logged in.", Toast.LENGTH_SHORT).show()
            return
        }
        val soundPickerFragment = SoundPickerBottomSheetFragment.newInstance()
        soundPickerFragment.setSoundSelectionListener(this) // `this` implement SoundSelectionListener
        soundPickerFragment.show(supportFragmentManager, SoundPickerBottomSheetFragment.TAG)
    }

    override fun onSoundSelected(soundItem: SoundItem) {
        if (recipientUserIdFromIntent == null) {
            Toast.makeText(this, "Cannot send sound: Recipient missing.", Toast.LENGTH_SHORT).show()
            return
        }
        Log.d(TAG, "Sound selected: ${soundItem.title}, URL: ${soundItem.soundUrl}")
        viewModel.sendMessage(
            messageText = "Sent a sound: ${soundItem.title}",
            messageType = "sound",
            fileUrl = soundItem.soundUrl,
            soundTitle = soundItem.title
        )
    }

    // Tidak perlu onStop() atau onDestroy() untuk melepaskan listener Firebase
    // karena ViewModel akan menanganinya di onCleared().
}