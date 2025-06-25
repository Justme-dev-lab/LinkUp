package com.example.linkup

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
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.linkup.adapter.MessageAdapter
import com.example.linkup.databinding.ActivityMessageChatBinding
import com.example.linkup.model.ChatMessageModel
import com.example.linkup.model.SoundItem
// import com.example.linkup.model.Chat // Pastikan model Chat Anda ada jika digunakan di sini
import com.example.linkup.ui.chats.SoundPickerBottomSheetFragment
import com.example.linkup.ui.chats.SoundSelectionListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.util.UUID

class MessageChatActivity : AppCompatActivity(), SoundSelectionListener {

    private lateinit var binding: ActivityMessageChatBinding

    private var firebaseUser: FirebaseUser? = null
    private var currentUserId: String? = null
    private var recipientUserId: String? = null
    private var recipientUserName: String? = null
    private var recipientProfileImageUrl: String? = null

    private lateinit var messageAdapter: MessageAdapter
    private val messagesList: MutableList<ChatMessageModel> = mutableListOf()

    private lateinit var messagesRef: DatabaseReference
    private var messagesListener: ChildEventListener? = null

    private var chatDetailsRef: DatabaseReference? = null
    // private var chatStatusListener: ValueEventListener? = null // Tidak digunakan secara aktif di kode yang Anda berikan

    private var chatId: String? = null

    // Activity Result Launcher untuk memilih file
    private val attachmentPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                data?.data?.let { fileUri ->
                    // Dapatkan tipe MIME dari file URI
                    val mimeType = contentResolver.getType(fileUri)
                    Log.d(TAG, "File selected: ${fileUri.path}, MIME type: $mimeType")

                    // Tentukan folder penyimpanan dan tipe pesan berdasarkan tipe MIME
                    val (storageFolder, messageType) = when {
                        mimeType?.startsWith("image/") == true -> "images" to "image"
                        mimeType?.startsWith("video/") == true -> "videos" to "video"
                        mimeType?.startsWith("audio/") == true -> "audios" to "audio"
                        // Tambahkan lebih banyak tipe jika perlu (misalnya, "application/pdf" -> "documents" to "pdf")
                        else -> "files" to "file" // Default untuk tipe lain atau tidak dikenal
                    }
                    uploadFileToStorage(fileUri, storageFolder, messageType)
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

        recipientUserId = intent.getStringExtra(EXTRA_USER_ID)
        recipientUserName = intent.getStringExtra(EXTRA_USER_NAME)
        recipientProfileImageUrl = intent.getStringExtra(EXTRA_PROFILE_IMAGE_URL)

        firebaseUser = FirebaseAuth.getInstance().currentUser
        currentUserId = firebaseUser?.uid

        if (recipientUserId == null || currentUserId == null) {
            Toast.makeText(this, "Error: User data missing.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Recipient User ID or Current User ID is null. Finishing activity.")
            finish()
            return
        }

        chatId = if (currentUserId!! < recipientUserId!!) {
            "${currentUserId!!}-$recipientUserId"
        } else {
            "$recipientUserId-${currentUserId!!}"
        }

        messagesRef = FirebaseDatabase.getInstance().getReference("messages").child(chatId!!)
        chatDetailsRef = FirebaseDatabase.getInstance().getReference("chats").child(chatId!!)

        setupToolbar()
        setupRecyclerView()

        binding.sendMessageBtn.setOnClickListener {
            val messageText: String = binding.textMessage.text.toString().trim()
            if (messageText.isEmpty()) {
                Toast.makeText(this@MessageChatActivity, "Please write a message", Toast.LENGTH_SHORT).show()
            } else {
                sendMessageToUser(
                    senderId = currentUserId!!,
                    receiverId = recipientUserId!!,
                    message = messageText,
                    messageType = "text" // Tipe default untuk pesan teks
                )
                binding.textMessage.setText("")
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
            Log.e(TAG, "Sound button (soundBtnChat) not found in binding. Check your XML ID.", e)
            Toast.makeText(this, "Sound button feature is not available.", Toast.LENGTH_SHORT).show()
        }

        attachMessagesListener()
    }

    override fun onResume() {
        super.onResume()
        markChatMessagesAsReadOrDelivered()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarChat)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.toolbarChat.setNavigationOnClickListener {
            finish()
        }

        binding.usernameToolbarChat.text = recipientUserName ?: "User"
        if (!recipientProfileImageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(recipientProfileImageUrl)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(binding.profileImageToolbarChat)
        } else {
            binding.profileImageToolbarChat.setImageResource(R.drawable.profile)
        }
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(this, messagesList, currentUserId!!, recipientProfileImageUrl)
        binding.recycleViewChats.apply {
            setHasFixedSize(true)
            val linearLayoutManager = LinearLayoutManager(this@MessageChatActivity)
            linearLayoutManager.stackFromEnd = true
            layoutManager = linearLayoutManager
            adapter = messageAdapter
        }
    }

    private fun sendMessageToUser(
        senderId: String,
        receiverId: String,
        message: String, // Bisa berupa teks pesan, nama file, atau deskripsi attachment
        messageType: String, // "text", "image", "video", "audio", "file", "sound"
        fileUrl: String? = null,
        soundTitle: String? = null,
        fileName: String? = null // Tambahkan parameter untuk nama file
    ) {
        val messagePushRef = messagesRef.push()
        val messageId = messagePushRef.key ?: ""

        val messageData = HashMap<String, Any>()
        messageData["messageId"] = messageId
        messageData["sender"] = senderId
        messageData["receiver"] = receiverId
        messageData["message"] = message // Untuk teks atau deskripsi/nama file attachment
        messageData["timestamp"] = System.currentTimeMillis()
        messageData["type"] = messageType

        if (fileUrl != null) {
            messageData["fileUrl"] = fileUrl
        }
        if (fileName != null && messageType != "text" && messageType != "sound") {
            messageData["fileName"] = fileName // Simpan nama file untuk attachment
        }
        if (soundTitle != null && messageType == "sound") {
            messageData["soundTitle"] = soundTitle
        }

        messagePushRef.setValue(messageData)
            .addOnSuccessListener {
                Log.d(TAG, "Message sent successfully to 'messages' node (type: $messageType).")
                val lastMessageDisplay = when (messageType) {
                    "text" -> message
                    "sound" -> "Sent a sound: ${soundTitle ?: "Sound"}"
                    "image" -> "📷 ${fileName ?: "Image"}" // Tampilkan nama file jika ada
                    "video" -> "📹 ${fileName ?: "Video"}"
                    "audio" -> "🎵 ${fileName ?: "Audio"}" // Untuk file audio umum
                    "file" -> "📄 ${fileName ?: "File"}"
                    else -> "Sent an attachment"
                }
                updateChatListNode(
                    participant1Id = senderId,
                    participant2Id = receiverId,
                    lastMessage = lastMessageDisplay,
                    timestamp = System.currentTimeMillis(),
                    actualLastMessageSenderId = senderId,
                    newStatus = "sent"
                )
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to send message: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Failed to send message to 'messages' node", e)
            }
    }

    private fun updateChatListNode(
        participant1Id: String,
        participant2Id: String,
        lastMessage: String,
        timestamp: Long,
        actualLastMessageSenderId: String,
        newStatus: String
    ) {
        if (chatId == null || chatDetailsRef == null) {
            Log.e(TAG, "chatId or chatDetailsRef is null in updateChatListNode. Cannot update chat node.")
            return
        }

        val chatInfo = mutableMapOf<String, Any>(
            "lastMessage" to lastMessage,
            "lastMessageTime" to timestamp,
            "lastMessageSenderId" to actualLastMessageSenderId,
            "participants" to mapOf(
                participant1Id to true,
                participant2Id to true
            ),
            "isGroupChat" to false,
            "lastMessageStatus" to newStatus
        )

        chatDetailsRef!!.updateChildren(chatInfo)
            .addOnSuccessListener { Log.d(TAG, "Chat list node '$chatId' updated. Sender: $actualLastMessageSenderId, Status: $newStatus") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to update chat list node '$chatId'", e) }
    }


    private fun markChatMessagesAsReadOrDelivered() {
        if (chatId == null || chatDetailsRef == null || currentUserId == null) {
            Log.w(TAG, "markChatMessagesAsReadOrDelivered: Essential data missing (chatId, chatDetailsRef, or currentUserId).")
            return
        }

        chatDetailsRef!!.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Log.w(TAG, "markChatMessagesAsReadOrDelivered: Chat node $chatId does not exist.")
                    return
                }

                val lastMessageSenderId = snapshot.child("lastMessageSenderId").getValue(String::class.java)
                val currentStatus = snapshot.child("lastMessageStatus").getValue(String::class.java)

                if (lastMessageSenderId != null && lastMessageSenderId != currentUserId) {
                    var newStatusToSet: String? = null
                    if (currentStatus == "sent") {
                        newStatusToSet = "read"
                        Log.d(TAG, "Chat $chatId: Last message from $lastMessageSenderId was 'sent'. Marking as 'read'.")
                    } else if (currentStatus == "delivered" && currentStatus != "read") {
                        newStatusToSet = "read"
                        Log.d(TAG, "Chat $chatId: Last message from $lastMessageSenderId was 'delivered'. Marking as 'read'.")
                    }

                    if (newStatusToSet != null) {
                        chatDetailsRef!!.child("lastMessageStatus").setValue(newStatusToSet)
                            .addOnSuccessListener {
                                Log.d(TAG, "Chat $chatId: Status updated to '$newStatusToSet' for message from $lastMessageSenderId.")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Chat $chatId: Failed to update status to '$newStatusToSet'.", e)
                            }
                    } else {
                        Log.d(TAG, "Chat $chatId: No status update needed. Current status: '$currentStatus', Sender: $lastMessageSenderId")
                    }
                } else {
                    Log.d(TAG, "Chat $chatId: Last message is from current user or no sender. No status change by receiver. Sender: $lastMessageSenderId, Current Status: $currentStatus")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to read chat details for marking as read/delivered.", error.toException())
            }
        })
    }


    private fun attachMessagesListener() {
        if (messagesListener != null) {
            messagesRef.removeEventListener(messagesListener!!)
        }
        messagesList.clear()

        messagesListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    val chatMessage = snapshot.getValue(ChatMessageModel::class.java)
                    chatMessage?.messageId = snapshot.key ?: ""
                    if (chatMessage != null) {
                        messagesList.add(chatMessage)
                        messageAdapter.notifyItemInserted(messagesList.size - 1)
                        binding.recycleViewChats.scrollToPosition(messagesList.size - 1)

                        if (chatMessage.sender == recipientUserId && firebaseUser?.uid == currentUserId) {
                            chatDetailsRef?.addListenerForSingleValueEvent(object: ValueEventListener {
                                override fun onDataChange(chatSnapshot: DataSnapshot) {
                                    val currentChatStatus = chatSnapshot.child("lastMessageStatus").getValue(String::class.java)
                                    val lastChatSender = chatSnapshot.child("lastMessageSenderId").getValue(String::class.java)
                                    if (lastChatSender == recipientUserId && currentChatStatus == "sent") {
                                        updateChatListNode(
                                            participant1Id = currentUserId!!,
                                            participant2Id = recipientUserId!!,
                                            lastMessage = chatMessage.message ?: "Attachment",
                                            timestamp = chatMessage.timestamp,
                                            actualLastMessageSenderId = chatMessage.sender!!,
                                            newStatus = "delivered"
                                        )
                                        Log.d(TAG, "Chat $chatId: Marked as 'delivered' upon receiving message in active chat.")
                                    }
                                }
                                override fun onCancelled(error: DatabaseError) {
                                    Log.w(TAG, "Failed to check chat status for delivered update", error.toException())
                                }
                            })
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing new message from Firebase.", e)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    val changedMessage = snapshot.getValue(ChatMessageModel::class.java)
                    changedMessage?.messageId = snapshot.key ?: ""
                    if (changedMessage != null) {
                        val index = messagesList.indexOfFirst { it.messageId == changedMessage.messageId }
                        if (index != -1) {
                            messagesList[index] = changedMessage
                            messageAdapter.notifyItemChanged(index)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing changed message from Firebase.", e)
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val removedMessageId = snapshot.key
                val index = messagesList.indexOfFirst { it.messageId == removedMessageId }
                if (index != -1) {
                    messagesList.removeAt(index)
                    messageAdapter.notifyItemRemoved(index)
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) { /* Not used */ }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to load messages.", error.toException())
                Toast.makeText(this@MessageChatActivity, "Failed to load messages.", Toast.LENGTH_SHORT).show()
            }
        }
        messagesRef.addChildEventListener(messagesListener!!)
    }

    private fun openAttachmentPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*" // Memungkinkan semua jenis file untuk dipilih
            addCategory(Intent.CATEGORY_OPENABLE)
            // Untuk memilih multiple file (opsional):
            // putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        try {
            attachmentPickerLauncher.launch(Intent.createChooser(intent, "Select a File"))
        } catch (ex: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "Please install a File Manager.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadFileToStorage(fileUri: Uri, storageFolder: String, messageType: String) {
        if (currentUserId == null || recipientUserId == null || chatId == null) {
            Toast.makeText(this, "Cannot upload file: User or chat data missing.", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "uploadFileToStorage: currentUserId, recipientUserId, or chatId is null.")
            return
        }

        // Tampilkan ProgressBar (Anda perlu menambahkannya ke layout XML Anda)
        // binding.uploadProgressBar.visibility = View.VISIBLE // Contoh

        val originalFileName = getFileName(fileUri) ?: "file" // Dapatkan nama file asli
        // Buat nama file unik di storage, tapi tetap pertahankan ekstensi asli jika ada
        val fileExtension = originalFileName.substringAfterLast('.', "")
        val uniqueFileNameInStorage = if (fileExtension.isNotEmpty()) {
            "${UUID.randomUUID()}_${System.currentTimeMillis()}.$fileExtension"
        } else {
            "${UUID.randomUUID()}_${System.currentTimeMillis()}"
        }


        val storageRef: StorageReference = FirebaseStorage.getInstance().reference
            .child("chat_attachments")
            .child(chatId!!) // Menggunakan chatId untuk subfolder
            .child(storageFolder) // "images", "videos", "files", "audios"
            .child(uniqueFileNameInStorage)

        Log.d(TAG, "Uploading to: ${storageRef.path}")

        storageRef.putFile(fileUri)
            .addOnSuccessListener { taskSnapshot ->
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    val downloadUrl = uri.toString()
                    Log.d(TAG, "File uploaded successfully. Download URL: $downloadUrl. Original name: $originalFileName")

                    // Teks pesan bisa berupa nama file untuk tipe attachment
                    val messageTextForAttachment = when (messageType) {
                        "image" -> "Image: $originalFileName" // Atau bisa kosong jika MessageAdapter menangani tampilan
                        "video" -> "Video: $originalFileName"
                        "audio" -> "Audio: $originalFileName"
                        "file" -> "File: $originalFileName"
                        else -> originalFileName // Fallback
                    }

                    sendMessageToUser(
                        senderId = currentUserId!!,
                        receiverId = recipientUserId!!,
                        message = messageTextForAttachment, // Teks deskriptif atau nama file
                        messageType = messageType,
                        fileUrl = downloadUrl,
                        fileName = originalFileName // Kirim nama file asli untuk ditampilkan
                    )
                    // binding.uploadProgressBar.visibility = View.GONE
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get download URL", e)
                    Toast.makeText(this, "Failed to get download URL: ${e.message}", Toast.LENGTH_SHORT).show()
                    // binding.uploadProgressBar.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to upload file to Firebase Storage", e)
                Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                // binding.uploadProgressBar.visibility = View.GONE
            }
            .addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                Log.d(TAG, "Upload is $progress% done")
                // binding.uploadProgressBar.progress = progress.toInt() // Update progress bar
            }
    }

    // Fungsi helper untuk mendapatkan nama file dari URI
    @SuppressLint("Range")
    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting file name from content URI", e)
            } finally {
                cursor?.close()
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
            Toast.makeText(this, "You need to be logged in to send sounds.", Toast.LENGTH_SHORT).show()
            return
        }
        val soundPickerFragment = SoundPickerBottomSheetFragment.newInstance()
        soundPickerFragment.setSoundSelectionListener(this)
        soundPickerFragment.show(supportFragmentManager, SoundPickerBottomSheetFragment.TAG)
    }

    override fun onSoundSelected(soundItem: SoundItem) {
        if (currentUserId == null || recipientUserId == null) {
            Toast.makeText(this, "Cannot send sound: User data missing.", Toast.LENGTH_SHORT).show()
            return
        }
        Log.d(TAG, "Sound selected: ${soundItem.title}, URL: ${soundItem.soundUrl}")
        sendMessageToUser(
            senderId = currentUserId!!,
            receiverId = recipientUserId!!,
            message = "Sent a sound: ${soundItem.title}", // Teks placeholder atau judul suara
            messageType = "sound",
            fileUrl = soundItem.soundUrl,
            soundTitle = soundItem.title
        )
    }

    override fun onStop() {
        super.onStop()
        messagesListener?.let {
            messagesRef.removeEventListener(it)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        messagesListener?.let {
            messagesRef.removeEventListener(it)
        }
        messagesListener = null
        // chatStatusListener tidak diinisialisasi, jadi tidak perlu dihapus
    }
}