package com.example.linkup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
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

class MessageChatActivity : AppCompatActivity(), SoundSelectionListener {

    private lateinit var binding: ActivityMessageChatBinding

    private var firebaseUser: FirebaseUser? = null
    private var currentUserId: String? = null // Tambahkan ini untuk konsistensi
    private var recipientUserId: String? = null
    private var recipientUserName: String? = null
    private var recipientProfileImageUrl: String? = null

    private lateinit var messageAdapter: MessageAdapter
    private val messagesList: MutableList<ChatMessageModel> = mutableListOf()

    private lateinit var messagesRef: DatabaseReference // Untuk messages/{chatId}
    private var messagesListener: ChildEventListener? = null

    private var chatDetailsRef: DatabaseReference? = null // Untuk chats/{chatId}
    private var chatStatusListener: ValueEventListener? = null // Listener untuk status di chats node

    private var chatId: String? = null

    // Activity Result Launcher untuk memilih file
    private val attachmentPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                data?.data?.let { fileUri ->
                    Toast.makeText(this, "File selected: ${fileUri.toString()}", Toast.LENGTH_LONG).show()
                    // Implementasi upload dan pengiriman pesan attachment...
                    // uploadFileToStorage(fileUri)
                }
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

        // Tentukan chatId secara konsisten
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
                sendMessageToUser(currentUserId!!, recipientUserId!!, messageText)
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
        // Tidak perlu attachChatStatusListener di sini jika hanya untuk update "read" saat activity dibuka.
        // markChatAsRead akan menangani ini saat activity dibuka.
        // Jika Anda ingin update "delivered" secara real-time saat penerima online, listener ini diperlukan.
    }

    override fun onResume() {
        super.onResume()
        // Penting: Tandai chat sebagai "read" ketika activity menjadi visible oleh pengguna.
        // Juga, ini adalah tempat yang baik untuk memperbarui status menjadi "delivered" jika belum.
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
        // Asumsi MessageAdapter Anda tidak memerlukan recipientProfileImageUrl jika sudah di-handle
        // di item layout atau Anda mengambilnya dari user data di adapter.
        // Jika MessageAdapter memerlukan currentUserId untuk logika tampilan (misal, alignment pesan),
        // pastikan itu diteruskan.
        messageAdapter = MessageAdapter(this, messagesList, currentUserId!!, recipientProfileImageUrl)
        binding.recycleViewChats.apply {
            setHasFixedSize(true)
            val linearLayoutManager = LinearLayoutManager(this@MessageChatActivity)
            linearLayoutManager.stackFromEnd = true // Pesan baru muncul di bawah
            layoutManager = linearLayoutManager
            adapter = messageAdapter
        }
    }

    private fun sendMessageToUser(
        senderId: String,
        receiverId: String,
        message: String,
        messageType: String = "text",
        fileUrl: String? = null,
        soundTitle: String? = null
    ) {
        val messagePushRef = messagesRef.push() // Dapatkan referensi push dulu untuk ID pesan
        val messageId = messagePushRef.key ?: "" // Dapatkan ID pesan unik

        val messageData = HashMap<String, Any>()
        messageData["messageId"] = messageId // Simpan ID pesan jika perlu
        messageData["sender"] = senderId
        messageData["receiver"] = receiverId
        messageData["message"] = message
        messageData["timestamp"] = System.currentTimeMillis()
        messageData["type"] = messageType
        // messageData["status"] = "sent" // <-- Status per pesan, jika Anda ingin melacaknya di node 'messages'

        if (fileUrl != null) {
            messageData["fileUrl"] = fileUrl
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
                    else -> "Sent an attachment"
                }
                // Sekarang update node 'chats' dengan status "sent"
                updateChatListNode(
                    participant1Id = senderId,
                    participant2Id = receiverId,
                    lastMessage = lastMessageDisplay,
                    timestamp = System.currentTimeMillis(),
                    actualLastMessageSenderId = senderId,
                    newStatus = "sent" // <--- STATUS "SENT" SAAT MENGIRIM
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
        newStatus: String // Tambahkan parameter status baru
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
            "lastMessageStatus" to newStatus // <--- GUNAKAN newStatus DI SINI
        )

        // Jika penerima adalah partisipan1, tambahkan unreadCount untuknya (opsional)
        // if (participant1Id != actualLastMessageSenderId) {
        //     chatInfo["unreadCount_${participant1Id}"] = FieldValue.increment(1)
        // } else if (participant2Id != actualLastMessageSenderId) {
        //     chatInfo["unreadCount_${participant2Id}"] = FieldValue.increment(1)
        // }


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

                // Hanya proses jika pesan terakhir BUKAN dari pengguna saat ini
                if (lastMessageSenderId != null && lastMessageSenderId != currentUserId) {
                    var newStatusToSet: String? = null

                    // Logika:
                    // 1. Jika status saat ini "sent" (dari pengirim), dan activity ini dibuka oleh penerima,
                    //    maka pesan dianggap "delivered" (sampai ke aplikasi penerima yang aktif)
                    //    dan karena activity chat dibuka, langsung "read".
                    // 2. Jika status saat ini "delivered" (mungkin dari FCM atau mekanisme lain),
                    //    dan activity chat dibuka, maka update ke "read".

                    if (currentStatus == "sent") {
                        newStatusToSet = "read" // Langsung "read" karena chatnya dibuka
                        Log.d(TAG, "Chat $chatId: Last message from $lastMessageSenderId was 'sent'. Marking as 'read'.")
                    } else if (currentStatus == "delivered" && currentStatus != "read") {
                        newStatusToSet = "read"
                        Log.d(TAG, "Chat $chatId: Last message from $lastMessageSenderId was 'delivered'. Marking as 'read'.")
                    }
                    // Tambahkan logika untuk unread count di sini jika ada
                    // chatDetailsRef!!.child("unreadCount_${currentUserId}").setValue(0)


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
        // messageAdapter.notifyDataSetChanged() // Tidak perlu jika submitList digunakan oleh adapter, atau jika ini sebelum adapter di-set

        messagesListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    val chatMessage = snapshot.getValue(ChatMessageModel::class.java)
                    chatMessage?.messageId = snapshot.key ?: "" // Ambil key sebagai messageId
                    if (chatMessage != null) {
                        messagesList.add(chatMessage)
                        messageAdapter.notifyItemInserted(messagesList.size - 1)
                        binding.recycleViewChats.scrollToPosition(messagesList.size - 1)

                        // Logika "Delivered" Sederhana (ketika penerima aktif dan memuat pesan):
                        // Jika pesan ini dari lawan bicara DAN belum ada status "delivered" atau "read" di node 'chats'
                        // maka kita bisa update node 'chats' ke "delivered".
                        // Ini akan terjadi jika penerima membuka chat sebelum 'markChatMessagesAsReadOrDelivered' di onResume sempat berjalan untuk status "sent".
                        if (chatMessage.sender == recipientUserId && firebaseUser?.uid == currentUserId) {
                            // Pengguna saat ini adalah penerima pesan ini.
                            // Cek status di node 'chats'
                            chatDetailsRef?.addListenerForSingleValueEvent(object: ValueEventListener {
                                override fun onDataChange(chatSnapshot: DataSnapshot) {
                                    val currentChatStatus = chatSnapshot.child("lastMessageStatus").getValue(String::class.java)
                                    val lastChatSender = chatSnapshot.child("lastMessageSenderId").getValue(String::class.java)
                                    // Hanya update ke delivered jika pesan terakhir adalah dari lawan dan statusnya masih 'sent'
                                    if (lastChatSender == recipientUserId && currentChatStatus == "sent") {
                                        updateChatListNode(
                                            participant1Id = currentUserId!!, // urutan bisa jadi penting jika chatId bergantung padanya
                                            participant2Id = recipientUserId!!,
                                            lastMessage = chatMessage.message ?: "Attachment", // Gunakan pesan aktual
                                            timestamp = chatMessage.timestamp, // Gunakan timestamp aktual
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
                // ... (implementasi Anda sudah baik)
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Biasanya tidak digunakan
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to load messages.", error.toException())
                Toast.makeText(this@MessageChatActivity, "Failed to load messages.", Toast.LENGTH_SHORT).show()
            }
        }
        messagesRef.addChildEventListener(messagesListener!!)
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
            message = "Sent a sound: ${soundItem.title}",
            messageType = "sound",
            fileUrl = soundItem.soundUrl,
            soundTitle = soundItem.title
        )
    }

    override fun onStop() {
        super.onStop()
        // Hapus listener pesan jika activity tidak lagi visible
        messagesListener?.let {
            messagesRef.removeEventListener(it)
            // messagesListener = null; // Bisa di-null-kan di sini jika tidak ada onRestart yang mengandalkannya
        }
        // Hapus listener status chat jika ada
        chatStatusListener?.let {
            chatDetailsRef?.removeEventListener(it)
            // chatStatusListener = null;
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Pastikan semua listener dihapus untuk menghindari memory leak
        messagesListener?.let {
            messagesRef.removeEventListener(it)
        }
        messagesListener = null

        chatStatusListener?.let {
            chatDetailsRef?.removeEventListener(it)
        }
        chatStatusListener = null
    }

    // onRestart tidak selalu dibutuhkan jika onResume sudah menangani attach listener
    // Namun, jika Anda secara eksplisit detach di onStop, Anda perlu re-attach di onStart atau onRestart.
    // Karena attachMessagesListener dipanggil di onCreate dan markChatMessagesAsReadOrDelivered di onResume,
    // seharusnya sudah cukup.
    // override fun onRestart() {
    //     super.onRestart()
    //     if (chatId != null && ::messagesRef.isInitialized) {
    //         attachMessagesListener() // Pasang kembali listener pesan
    //         // Jika Anda menggunakan chatStatusListener untuk real-time update, pasang juga di sini
    //     }
    //     // Anda mungkin juga ingin memanggil markChatMessagesAsReadOrDelivered() di sini
    //     // atau pastikan onResume melakukannya.
    // }
}