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
    private var isChatSetupCompleted: Boolean = false // Flag untuk status setup

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
            Log.e(TAG, "setupChat: Current user ID is null. Cannot proceed.")
            _isLoading.postValue(false) // Gunakan postValue
            isChatSetupCompleted = false
            return
        }

        if (recipientId.isBlank()) { // Tambahan pemeriksaan untuk recipientId
            Log.e(TAG, "setupChat: Recipient ID is blank. Cannot proceed.")
            _isLoading.postValue(false) // GANTI KE postValue
            isChatSetupCompleted = false
            return
        }

        chatId = if (currentUserId!! < recipientId) {
            "${currentUserId!!}-$recipientId"
        } else {
            "$recipientId-${currentUserId!!}"
        }

        // Validasi chatId (opsional, tapi bisa membantu jika ada karakter aneh)
        if (chatId!!.contains(Regex("[.#$\\[\\]]"))) {
            Log.e(TAG, "setupChat: Generated chatId '$chatId' contains invalid characters.")
            _isLoading.postValue(false) // GANTI KE postValue
            isChatSetupCompleted = false
            this.chatId = null
            return
        }

        Log.i(TAG, "setupChat: Chat configured. chatId: $chatId")
        messagesRef = database.getReference("messages").child(chatId!!)
        chatDetailsRef = database.getReference("chats").child(chatId!!)

        isChatSetupCompleted = true // Tandai setup selesai

        // Lanjutkan dengan attachMessagesListenerInternal dan markChatMessagesAsReadOrDeliveredOnSetup
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
        if (!isChatSetupCompleted || chatId == null) {
            Log.e(TAG, "sendMessage: Chat setup is not complete or chatId is null. Cannot send message. isChatSetupCompleted: $isChatSetupCompleted, chatId: $chatId")
            _messageSentStatus.postValue(Event(false)) // Gunakan postValue jika ini bisa dipanggil dari berbagai thread
            _isLoading.postValue(false)
            return
        }

        if (currentUserId == null || recipientUserId == null) {
            _messageSentStatus.postValue(Event(false))
            Log.e(TAG, "Cannot send message: User or chat data missing.")
            return
        }

        val sender = currentUserId!!
        val receiver = recipientUserId!!

        _isLoading.postValue(true) // Gunakan postValue jika sendMessage bisa dipanggil dari background thread
        // Atau, jika Anda selalu membungkus panggilan ke sendMessage dengan withContext(Dispatchers.Main)
        // dari uploadFileToStorage, maka _isLoading.value = true aman di dalam withContext(Dispatchers.Main) tersebut.

        viewModelScope.launch { // Default ke Dispatchers.Main.immediate jika coroutine builder tidak menentukan dispatcher lain
            try {
                if (!::messagesRef.isInitialized) {
                    Log.e(TAG, "sendMessage: messagesRef is not initialized!")
                    _messageSentStatus.value = Event(false)
                    _isLoading.value = false
                    return@launch
                }

                val messagePushRef = messagesRef.push()
                val messageId = messagePushRef.key ?: UUID.randomUUID().toString()

                val messageData = HashMap<String, Any>()
                messageData["messageId"] = messageId
                messageData["sender"] = sender
                messageData["receiver"] = receiver
                messageData["message"] = messageText
                messageData["timestamp"] = System.currentTimeMillis()
                messageData["type"] = messageType
                messageData["isseen"] = false

                fileUrl?.let { messageData["fileUrl"] = it }
                fileName?.let { if (messageType != "text" && messageType != "sound") messageData["fileName"] = it }
                soundTitle?.let { if (messageType == "sound") messageData["soundTitle"] = it }

                // Operasi database sekarang akan dieksekusi di Main thread karena context coroutine launch ini
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
                    newStatus = "sent"
                ).await() // Ini juga akan berjalan di Main thread

                _messageSentStatus.value = Event(true)

            } catch (e: Exception) {
                Log.e(TAG, "Error sending message", e)
                _messageSentStatus.value = Event(false)
            } finally {
                _isLoading.value = false
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
        // 1. Pemeriksaan awal: Apakah setup chat sudah selesai dan chatId ada?
        if (!isChatSetupCompleted || chatId == null) {
            val errorMessage = "updateChatListNodeInternal: Chat setup not complete or chatId is null. " +
                    "isChatSetupCompleted: $isChatSetupCompleted, chatId: $chatId"
            Log.e(TAG, errorMessage)
            return Tasks.forException(IllegalStateException(errorMessage))
        }

        // 2. Pemeriksaan kedua: Apakah referensi database (chatDetailsRef) sudah diinisialisasi?
        // Ini adalah pemeriksaan defensif; jika isChatSetupCompleted true dan chatId tidak null,
        // chatDetailsRef seharusnya sudah diinisialisasi di setupChat.
        if (!::chatDetailsRef.isInitialized) {
            val errorMessage = "updateChatListNodeInternal: chatDetailsRef is not initialized! This should not happen if chat setup was complete."
            Log.e(TAG, errorMessage)
            return Tasks.forException(IllegalStateException(errorMessage))
        }

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

        Log.d(TAG, "Attempting to update chat list node '$chatId'. Data: $chatInfo")

        // Jika chatId tidak null, lakukan update
        return chatDetailsRef.updateChildren(chatInfo)
            .addOnSuccessListener {
                Log.d(TAG, "Chat list node '$chatId' updated successfully. Sender: $actualLastMessageSenderId, Status: $newStatus")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update chat list node '$chatId'", e)
                // Anda mungkin ingin melakukan sesuatu di sini, tapi karena mengembalikan Task,
                // kegagalan akan ditangani oleh pemanggil yang melakukan .await()
            }
    }


    fun uploadFileToStorage(
        fileUri: Uri,
        storageFolder: String,
        messageType: String,
        originalFileName: String
    ) {
        if (originalFileName.isBlank()) { // Atau .isNullOrBlank() jika bisa null
            Log.e(TAG, "uploadFileToStorage: originalFileName is blank. Cannot proceed.")
            _fileUploadStatus.postValue(Event(Pair(false, "File name is missing.")))
            _isLoading.postValue(false)
            return
        }

        if (!isChatSetupCompleted || chatId == null) { // Pemeriksaan yang lebih ketat
            Log.e(TAG, "uploadFileToStorage: Chat setup is not complete or chatId is null. Cannot upload file. isChatSetupCompleted: $isChatSetupCompleted, chatId: $chatId")
            _fileUploadStatus.postValue(Event(Pair(false, "Chat session not ready.")))
            _isLoading.postValue(false)
            return
        }

        if (currentUserId == null || recipientUserId == null || chatId == null) {
            _fileUploadStatus.postValue(Event(Pair(false, "User or chat data missing."))) // GANTI
            return
        }

        _isLoading.postValue(true)
        _fileUploadProgress.postValue(Event(0))

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
//                    _isLoading.value = false // Pastikan isLoading false jika gagal
                }
            } finally {
                withContext(Dispatchers.Main) { // Selalu set isLoading false di akhir
                    _isLoading.value = false
                }
            }
        }
    }

    fun markChatMessagesAsReadOnResume() {
        // Periksa apakah setup sudah selesai dan chat ID ada
        if (!isChatSetupCompleted || chatId == null) {
            Log.w(TAG, "markChatMessagesAsReadOnResume: Chat setup not complete or chatId is null. Cannot proceed.")
            return
        }

        // Periksa apakah referensi sudah diinisialisasi (defensif, seharusnya sudah jika setup complete)
        if (!::chatDetailsRef.isInitialized || !::messagesRef.isInitialized) { // messagesRef juga mungkin relevan
            Log.w(TAG, "markChatMessagesAsReadOnResume: Database references not initialized. isChatSetupCompleted: $isChatSetupCompleted")
            return
        }

        // currentUserId dan recipientUserId juga penting
        if (currentUserId == null || recipientUserId == null) {
            Log.w(TAG, "markChatMessagesAsReadOnResume: User IDs are missing.")
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