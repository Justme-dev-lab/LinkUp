package com.example.linkup.ui.message // atau package yang sesuai

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.linkup.model.ChatMessageModel
import com.example.linkup.utils.Event
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class MessageChatViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private var currentUserId: String? = auth.currentUser?.uid
    private var recipientUserId: String? = null // Akan di-set oleh Activity

    private lateinit var messagesRef: DatabaseReference
    private lateinit var chatDetailsRef: DatabaseReference
    private var chatId: String? = null

    private val _messagesList = MutableLiveData<List<ChatMessageModel>>()
    val messagesList: LiveData<List<ChatMessageModel>> = _messagesList
    private val internalMessagesList = mutableListOf<ChatMessageModel>()

    private var messagesListener: ChildEventListener? = null

    private val _messageSentStatus = MutableLiveData<Event<Boolean>>()
    val messageSentStatus: LiveData<Event<Boolean>> = _messageSentStatus

    private val _fileUploadProgress = MutableLiveData<Event<Int>>() // Progress 0-100
    val fileUploadProgress: LiveData<Event<Int>> = _fileUploadProgress

    private val _fileUploadStatus = MutableLiveData<Event<Pair<Boolean, String?>>>() // Pair<Success, ErrorMessage?>
    val fileUploadStatus: LiveData<Event<Pair<Boolean, String?>>> = _fileUploadStatus

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    companion object {
        private const val TAG = "MessageChatViewModel"
    }

    fun setupChat(recipientId: String) {
        this.recipientUserId = recipientId
        if (currentUserId == null) {
            Log.e(TAG, "Current user ID is null. Cannot setup chat.")
            // Mungkin kirim event error ke Activity untuk menutup
            return
        }

        chatId = if (currentUserId!! < recipientId) {
            "${currentUserId!!}-$recipientId"
        } else {
            "$recipientId-${currentUserId!!}"
        }
        messagesRef = database.getReference("messages").child(chatId!!)
        chatDetailsRef = database.getReference("chats").child(chatId!!)
        attachMessagesListenerInternal()
        markChatMessagesAsReadOrDeliveredOnSetup()
    }

    private fun attachMessagesListenerInternal() {
        if (messagesListener != null) {
            messagesRef.removeEventListener(messagesListener!!)
        }
        internalMessagesList.clear()
        _messagesList.value = emptyList() // Trigger observer dengan list kosong awal

        messagesListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    val chatMessage = snapshot.getValue(ChatMessageModel::class.java)
                    chatMessage?.messageId = snapshot.key ?: ""
                    chatMessage?.let {
                        if (!internalMessagesList.any { m -> m.messageId == it.messageId }) {
                            internalMessagesList.add(it)
                            _messagesList.postValue(ArrayList(internalMessagesList)) // Post salinan baru
                        }
                        // Jika pesan diterima (bukan dari current user) dan chat aktif, update status ke delivered
                        if (it.sender == recipientUserId && it.sender != currentUserId) {
                            markMessageAsDelivered(it)
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
                    changedMessage?.let {
                        val index = internalMessagesList.indexOfFirst { m -> m.messageId == it.messageId }
                        if (index != -1) {
                            internalMessagesList[index] = it
                            _messagesList.postValue(ArrayList(internalMessagesList))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing changed message from Firebase.", e)
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val removedMessageId = snapshot.key
                val index = internalMessagesList.indexOfFirst { it.messageId == removedMessageId }
                if (index != -1) {
                    internalMessagesList.removeAt(index)
                    _messagesList.postValue(ArrayList(internalMessagesList))
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) { /* Not used */ }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Messages listener error", error.toException())
                // Mungkin kirim event error ke Activity
            }
        }
        messagesRef.addChildEventListener(messagesListener!!)
    }

    private fun markMessageAsDelivered(receivedMessage: ChatMessageModel) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chatSnapshot = chatDetailsRef.get().await() // Menggunakan .get().await() untuk operasi satu kali
                if (chatSnapshot.exists()) {
                    val lastChatSender = chatSnapshot.child("lastMessageSenderId").getValue(String::class.java)
                    val currentChatStatus = chatSnapshot.child("lastMessageStatus").getValue(String::class.java)

                    // Hanya update ke 'delivered' jika pengirim terakhir adalah penerima dan status saat ini adalah 'sent'
                    if (lastChatSender == recipientUserId && currentChatStatus == "sent") {
                        updateChatListNodeInternal(
                            participant1Id = currentUserId!!, // Diasumsikan currentUserId tidak null di sini
                            participant2Id = recipientUserId!!, // Diasumsikan recipientUserId tidak null
                            lastMessage = receivedMessage.message ?: "Attachment",
                            timestamp = receivedMessage.timestamp,
                            actualLastMessageSenderId = receivedMessage.sender ?: recipientUserId!!,
                            newStatus = "delivered"
                        ).await()
                        Log.d(TAG, "Chat $chatId: Marked as 'delivered' upon receiving message in active chat.")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check/update chat status for delivered", e)
            }
        }
    }

    fun sendMessage(
        messageText: String,
        messageType: String,
        fileUrl: String? = null,
        soundTitle: String? = null,
        fileName: String? = null
    ) {
        if (currentUserId == null || recipientUserId == null || chatId == null) {
            _messageSentStatus.value = Event(false)
            Log.e(TAG, "Cannot send message: User or chat data missing.")
            return
        }

        val sender = currentUserId!!
        val receiver = recipientUserId!!

        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val messagePushRef = messagesRef.push()
                val messageId = messagePushRef.key ?: UUID.randomUUID().toString()

                val messageData = HashMap<String, Any>()
                messageData["messageId"] = messageId
                messageData["sender"] = sender
                messageData["receiver"] = receiver
                messageData["message"] = messageText
                messageData["timestamp"] = System.currentTimeMillis()
                messageData["type"] = messageType
                messageData["isseen"] = false // Default

                fileUrl?.let { messageData["fileUrl"] = it }
                fileName?.let { if (messageType != "text" && messageType != "sound") messageData["fileName"] = it }
                soundTitle?.let { if (messageType == "sound") messageData["soundTitle"] = it }

                messagePushRef.setValue(messageData).await()
                Log.d(TAG, "Message sent to 'messages' node (type: $messageType).")

                val lastMessageDisplay = when (messageType) {
                    "text" -> messageText
                    "sound" -> "Sent a sound: ${soundTitle ?: "Sound"}"
                    "image" -> "📷 ${fileName ?: "Image"}"
                    "video" -> "📹 ${fileName ?: "Video"}"
                    "audio" -> "🎵 ${fileName ?: "Audio"}"
                    "file" -> "📄 ${fileName ?: "File"}"
                    else -> "Sent an attachment"
                }

                updateChatListNodeInternal(
                    participant1Id = sender,
                    participant2Id = receiver,
                    lastMessage = lastMessageDisplay,
                    timestamp = System.currentTimeMillis(),
                    actualLastMessageSenderId = sender,
                    newStatus = "sent" // Pesan yang dikirim selalu 'sent' awalnya
                ).await()

                withContext(Dispatchers.Main) {
                    _messageSentStatus.value = Event(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message", e)
                withContext(Dispatchers.Main) {
                    _messageSentStatus.value = Event(false)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    // Fungsi internal untuk updateChatListNode (mengembalikan Task untuk await)
    private fun updateChatListNodeInternal(
        participant1Id: String,
        participant2Id: String,
        lastMessage: String,
        timestamp: Long,
        actualLastMessageSenderId: String,
        newStatus: String
    ): Task<Void> { // Mengembalikan Task agar bisa di-await
        val chatInfo = mutableMapOf<String, Any>(
            "lastMessage" to lastMessage,
            "lastMessageTime" to timestamp,
            "lastMessageSenderId" to actualLastMessageSenderId,
            "participants" to mapOf(
                participant1Id to true,
                participant2Id to true
            ),
            "isGroupChat" to false, // Asumsi bukan group chat untuk konteks ini
            "lastMessageStatus" to newStatus
        )
        // Jika chatId tidak null, lakukan update
        return if (chatId != null) {
            chatDetailsRef.updateChildren(chatInfo)
                .addOnSuccessListener { Log.d(TAG, "Chat list node '$chatId' updated. Sender: $actualLastMessageSenderId, Status: $newStatus") }
                .addOnFailureListener { e -> Log.e(TAG, "Failed to update chat list node '$chatId'", e) }
        } else {
            // Kembalikan Task yang gagal jika chatId null
            Tasks.forException(IllegalStateException("chatId is null, cannot update chat node."))
        }
    }


    fun uploadFileToStorage(
        fileUri: Uri,
        storageFolder: String,
        messageType: String,
        originalFileName: String
    ) {
        if (currentUserId == null || recipientUserId == null || chatId == null) {
            _fileUploadStatus.value = Event(Pair(false, "User or chat data missing."))
            return
        }

        _isLoading.value = true
        _fileUploadProgress.value = Event(0) // Mulai progress

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileExtension = originalFileName.substringAfterLast('.', "")
                val uniqueFileNameInStorage = if (fileExtension.isNotEmpty()) {
                    "${UUID.randomUUID()}_${System.currentTimeMillis()}.$fileExtension"
                } else {
                    "${UUID.randomUUID()}_${System.currentTimeMillis()}"
                }

                val fileStorageRef = storage.reference
                    .child("chat_attachments")
                    .child(chatId!!)
                    .child(storageFolder)
                    .child(uniqueFileNameInStorage)

                Log.d(TAG, "Uploading to: ${fileStorageRef.path}")

                val uploadTask = fileStorageRef.putFile(fileUri)

                // Listener untuk progress (opsional jika tidak ingin coroutine menunggu)
                // Ini akan berjalan di thread yang berbeda dari launch IO, jadi hati-hati dengan update LiveData
                // Mungkin lebih baik menggunakan addOnProgressListener di luar coroutine utama jika progres sangat detail.
                // Untuk kesederhanaan, kita await saja.

                uploadTask.addOnProgressListener { taskSnapshot ->
                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                    _fileUploadProgress.postValue(Event(progress)) // postValue karena mungkin dari thread lain
                }.await() // Menunggu upload selesai

                val downloadUrl = fileStorageRef.downloadUrl.await().toString()
                Log.d(TAG, "File uploaded. URL: $downloadUrl. Original: $originalFileName")

                val messageTextForAttachment = when (messageType) {
                    "image" -> "Image: $originalFileName"
                    "video" -> "Video: $originalFileName"
                    "audio" -> "Audio: $originalFileName"
                    "file" -> "File: $originalFileName"
                    else -> originalFileName
                }

                // Kirim pesan setelah file berhasil diupload
                sendMessage(
                    messageText = messageTextForAttachment,
                    messageType = messageType,
                    fileUrl = downloadUrl,
                    fileName = originalFileName
                )
                withContext(Dispatchers.Main) {
                    _fileUploadStatus.value = Event(Pair(true, null))
                    _fileUploadProgress.value = Event(100) // Selesai
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload file to Firebase Storage", e)
                withContext(Dispatchers.Main) {
                    _fileUploadStatus.value = Event(Pair(false, e.message ?: "Upload failed"))
                    _isLoading.value = false // Pastikan isLoading false jika gagal
                }
            } finally {
                withContext(Dispatchers.Main) { // Selalu set isLoading false di akhir
                    _isLoading.value = false
                }
            }
        }
    }

    fun markChatMessagesAsReadOnResume() {
        if (chatId == null || chatDetailsRef == null || currentUserId == null || recipientUserId == null) {
            Log.w(TAG, "markChatMessagesAsRead: Essential data missing.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = chatDetailsRef.get().await() // Menggunakan .get().await() untuk operasi satu kali
                if (!snapshot.exists()) {
                    Log.w(TAG, "markChatMessagesAsRead: Chat node $chatId does not exist.")
                    return@launch
                }

                val lastMessageSenderId = snapshot.child("lastMessageSenderId").getValue(String::class.java)
                val currentStatus = snapshot.child("lastMessageStatus").getValue(String::class.java)

                // Hanya tandai sebagai 'read' jika pesan terakhir bukan dari pengguna saat ini
                // dan statusnya 'sent' atau 'delivered'
                if (lastMessageSenderId != null && lastMessageSenderId == recipientUserId) {
                    var newStatusToSet: String? = null
                    if (currentStatus == "sent" || currentStatus == "delivered") {
                        newStatusToSet = "read"
                        Log.d(TAG, "Chat $chatId: Last message from $lastMessageSenderId. Marking as 'read'.")
                    }

                    if (newStatusToSet != null) {
                        // Tidak perlu update lastMessage atau timestamp, hanya status
                        updateChatListNodeInternal(
                            participant1Id = currentUserId!!,
                            participant2Id = recipientUserId!!,
                            lastMessage = snapshot.child("lastMessage").getValue(String::class.java) ?: "",
                            timestamp = snapshot.child("lastMessageTime").getValue(Long::class.java) ?: 0L,
                            actualLastMessageSenderId = lastMessageSenderId,
                            newStatus = newStatusToSet
                        ).await()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read/update chat details for marking as read.", e)
            }
        }
    }

    private fun markChatMessagesAsReadOrDeliveredOnSetup() { // Dipanggil saat setup
        markChatMessagesAsReadOnResume() // Logika yang sama bisa digunakan
    }


    override fun onCleared() {
        super.onCleared()
        messagesListener?.let {
            if (::messagesRef.isInitialized) { // Cek apakah sudah diinisialisasi
                messagesRef.removeEventListener(it)
            }
        }
        messagesListener = null
        Log.d(TAG, "ViewModel cleared, listeners removed.")
    }
}