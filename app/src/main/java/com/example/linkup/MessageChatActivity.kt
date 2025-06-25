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
import com.example.linkup.ui.chats.SoundPickerBottomSheetFragment
import com.example.linkup.ui.chats.SoundSelectionListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*

class MessageChatActivity : AppCompatActivity(), SoundSelectionListener {

    private lateinit var binding: ActivityMessageChatBinding

    private var firebaseUser: FirebaseUser? = null
    private var recipientUserId: String? = null
    private var recipientUserName: String? = null
    private var recipientProfileImageUrl: String? = null

    private lateinit var messageAdapter: MessageAdapter
    private val messagesList: MutableList<ChatMessageModel> = mutableListOf()

    private var messagesListener: ChildEventListener? = null
    private lateinit var messagesRef: DatabaseReference // Ini akan menunjuk ke messages/{chatId}
    private var chatId: String? = null

    // Activity Result Launcher untuk memilih file
    private val attachmentPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                data?.data?.let { fileUri ->
                    // Di sini Anda akan menangani URI file yang dipilih
                    // Misalnya, mengunggahnya ke Firebase Storage dan kemudian mengirim pesan
                    // dengan link ke file tersebut.
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
        // Tidak perlu ATTACHMENT_REQUEST_CODE jika menggunakan ActivityResultLauncher
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessageChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Penting untuk menangani System Insets agar layout tidak tertutup status bar
        // Pastikan root layout Anda di XML (misal R.id.main_chat_layout) memiliki android:fitsSystemWindows="true"
        // Jika Anda menggunakan AppBarLayout, biasanya ia sudah menangani insets untuk Toolbar.
        // ViewCompat.setOnApplyWindowInsetsListener(binding.mainChatLayout) { v, insets ->
        //     val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        //     v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
        //     insets
        // }

        recipientUserId = intent.getStringExtra(EXTRA_USER_ID)
        recipientUserName = intent.getStringExtra(EXTRA_USER_NAME)
        recipientProfileImageUrl = intent.getStringExtra(EXTRA_PROFILE_IMAGE_URL)

        firebaseUser = FirebaseAuth.getInstance().currentUser

        if (recipientUserId == null || firebaseUser == null) {
            Toast.makeText(this, "Error: User data missing.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Recipient User ID or FirebaseUser is null. Finishing activity.")
            finish()
            return
        }

        chatId = if (firebaseUser!!.uid < recipientUserId!!) {
            "${firebaseUser!!.uid}-$recipientUserId"
        } else {
            "$recipientUserId-${firebaseUser!!.uid}"
        }
        messagesRef = FirebaseDatabase.getInstance().getReference("messages").child(chatId!!)

        setupToolbar()
        setupRecyclerView()

        binding.sendMessageBtn.setOnClickListener {
            val messageText: String = binding.textMessage.text.toString().trim()
            if (messageText.isEmpty()) {
                Toast.makeText(this@MessageChatActivity, "Please write a message", Toast.LENGTH_SHORT).show()
            } else {
                sendMessageToUser(firebaseUser!!.uid, recipientUserId!!, messageText)
                binding.textMessage.setText("")
            }
        }

        binding.attachImageFileBtn.setOnClickListener {
            openAttachmentPicker()
        }

        // Pastikan ID 'soundBtnChat' ada di activity_message_chat.xml Anda
        // Jika binding tidak menemukannya, aplikasi akan crash.
        // Anda mungkin perlu clean & rebuild project setelah mengubah XML.
        try {
            binding.soundBtnChat.setOnClickListener {
                showSoundPickerBottomSheet() // Ganti ke fungsi baru
            }
        } catch (e: NullPointerException) {
            Log.e(TAG, "Sound button (soundBtnChat) not found in binding. Check your XML ID.", e)
            Toast.makeText(this, "Sound button feature is not available.", Toast.LENGTH_SHORT).show()
        }

        attachMessagesListener()
    }

    private fun setupToolbar() {
        // Gunakan ID toolbar yang baru dari XML (misal, R.id.toolbar_chat jika Anda menamainya demikian di XML)
        // Jika Anda tetap menggunakan ID 'toolbar' di XML baru, maka binding.toolbar sudah benar.
        // Untuk contoh ini, saya asumsikan Anda mengganti ID di XML menjadi 'toolbar_chat' seperti saran sebelumnya.
        setSupportActionBar(binding.toolbarChat) // Ganti ke binding.toolbarChat jika ID di XML diubah
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Handler untuk tombol kembali di toolbar
        // Jika Anda menggunakan binding.toolbarChat, ganti di sini juga
        binding.toolbarChat.setNavigationOnClickListener {
            finish()
        }

        // Gunakan ID yang baru untuk username dan profile image di toolbar
        // misal, R.id.username_toolbar_chat dan R.id.profile_image_toolbar_chat
        binding.usernameToolbarChat.text = recipientUserName ?: "User" // Ganti ke binding.usernameToolbarChat
        if (!recipientProfileImageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(recipientProfileImageUrl)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile) // Tambahkan error placeholder
                .into(binding.profileImageToolbarChat) // Ganti ke binding.profileImageToolbarChat
        } else {
            binding.profileImageToolbarChat.setImageResource(R.drawable.profile) // Ganti ke binding.profileImageToolbarChat
        }
    }

    private fun setupRecyclerView() {
        // Pastikan Anda sudah meneruskan recipientProfileImageUrl ke adapter jika diperlukan oleh adapter
        // Kode Anda sebelumnya sudah benar:
        messageAdapter = MessageAdapter(this, messagesList, firebaseUser!!.uid, recipientProfileImageUrl)
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
        message: String,
        messageType: String = "text",
        fileUrl: String? = null,
        soundTitle: String? = null // Parameter baru
    ) {
        val messageData = HashMap<String, Any>()
        messageData["sender"] = senderId
        messageData["receiver"] = receiverId
        messageData["message"] = message
        messageData["timestamp"] = System.currentTimeMillis()
        messageData["type"] = messageType
        if (fileUrl != null) {
            messageData["fileUrl"] = fileUrl
        }
        if (soundTitle != null && messageType == "sound") { // Simpan judul suara jika tipenya sound
            messageData["soundTitle"] = soundTitle
        }
        // messageData["isseen"] = false

        messagesRef.push().setValue(messageData)
            .addOnSuccessListener {
                Log.d(TAG, "Message sent successfully (type: $messageType).")
                val lastMessageDisplay = when (messageType) {
                    "text" -> message
                    "sound" -> "Sent a sound: ${soundTitle ?: "Sound"}"
                    else -> "Sent an attachment"
                }
                updateChatListNode(
                    participant1Id = senderId,
                    participant2Id = receiverId,
                    lastMessage = lastMessageDisplay,
                    timestamp = System.currentTimeMillis(),
                    actualLastMessageSenderId = senderId // ID pengirim pesan saat ini
                )
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to send message: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Failed to send message", e)
            }
    }

    private fun updateChatListNode(
        participant1Id: String,             // ID partisipan pertama dalam chat
        participant2Id: String,             // ID partisipan kedua dalam chat
        lastMessage: String,
        timestamp: Long,
        actualLastMessageSenderId: String // ID pengguna yang mengirim pesan terakhir ini
    ) {
        // chatId sudah harus ditentukan sebelumnya (misalnya di onCreate)
        // dan merujuk pada ID unik untuk percakapan antara participant1Id dan participant2Id.
        if (chatId == null) {
            Log.e(TAG, "chatId is null in updateChatListNode. Cannot update chat node.")
            return
        }
        val chatRef = FirebaseDatabase.getInstance().getReference("chats").child(chatId!!)

        val chatInfo = mutableMapOf<String, Any>(
            "lastMessage" to lastMessage,
            "lastMessageTime" to timestamp,
            "lastMessageSenderId" to actualLastMessageSenderId, // Menggunakan ID pengirim yang benar
            "participants" to mapOf(
                participant1Id to true, // Partisipan 1
                participant2Id to true  // Partisipan 2
            ),
            "isGroupChat" to false // Asumsi ini untuk chat 1-ke-1
            // "lastMessageStatus" to "sent" // Anda bisa menambahkan ini jika perlu
        )

        chatRef.updateChildren(chatInfo)
            .addOnSuccessListener { Log.d(TAG, "Chat list node updated for $chatId with sender $actualLastMessageSenderId") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to update chat list node for $chatId", e) }
    }

    private fun attachMessagesListener() {
        if (messagesListener != null) { // Hapus listener lama jika ada sebelum memasang yang baru
            messagesRef.removeEventListener(messagesListener!!)
            messagesListener = null
        }
        messagesList.clear() // Bersihkan list sebelum menambahkan listener baru
        messageAdapter.notifyDataSetChanged() // Beritahu adapter bahwa data kosong

        messagesListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val chatMessage = snapshot.getValue(ChatMessageModel::class.java)
                chatMessage?.messageId = snapshot.key ?: ""
                if (chatMessage != null) {
                    messagesList.add(chatMessage)
                    messageAdapter.notifyItemInserted(messagesList.size - 1)
                    binding.recycleViewChats.scrollToPosition(messagesList.size - 1)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val changedMessage = snapshot.getValue(ChatMessageModel::class.java)
                changedMessage?.messageId = snapshot.key ?: ""
                if (changedMessage != null) {
                    val index = messagesList.indexOfFirst { it.messageId == changedMessage.messageId }
                    if (index != -1) {
                        messagesList[index] = changedMessage
                        messageAdapter.notifyItemChanged(index)
                    }
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val removedMessageId = snapshot.key
                val index = messagesList.indexOfFirst { it.messageId == removedMessageId }
                if (index != -1) {
                    messagesList.removeAt(index)
                    messageAdapter.notifyItemRemoved(index)
                    // Anda mungkin ingin memberitahu pengguna bahwa pesan telah dihapus
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Biasanya tidak digunakan untuk chat sederhana
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
            type = "*/*" // Memungkinkan semua jenis file, bisa dispesifikkan misal "image/*"
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

    private fun showSoundPickerBottomSheet() {
        if (firebaseUser == null) {
            Toast.makeText(this, "You need to be logged in to send sounds.", Toast.LENGTH_SHORT).show()
            return
        }
        val soundPickerFragment = SoundPickerBottomSheetFragment.newInstance()
        soundPickerFragment.setSoundSelectionListener(this) // Set activity sebagai listener
        soundPickerFragment.show(supportFragmentManager, SoundPickerBottomSheetFragment.TAG)
    }

    override fun onSoundSelected(soundItem: SoundItem) {
        Log.d(TAG, "Sound selected: ${soundItem.title}, URL: ${soundItem.soundUrl}")
        // Kirim pesan dengan tipe "sound"
        // Pesan bisa berupa judul suara, dan URL akan ada di fileUrl
        sendMessageToUser(
            senderId = firebaseUser!!.uid,
            receiverId = recipientUserId!!,
            message = "Sent a sound: ${soundItem.title}", // Teks placeholder atau judul suara
            messageType = "sound",
            fileUrl = soundItem.soundUrl,
            soundTitle = soundItem.title // Tambahkan parameter baru
        )
    }

    override fun onStop() {
        super.onStop()
        messagesListener?.let {
            messagesRef.removeEventListener(it)
        }
        // Tidak perlu set messagesListener = null di sini jika Anda akan memasangnya lagi di onRestart
        // atau jika activity dihancurkan.
    }

    override fun onDestroy() { // Lebih baik remove listener di onDestroy jika activity benar-benar dihancurkan
        super.onDestroy()
        messagesListener?.let {
            messagesRef.removeEventListener(it)
        }
        messagesListener = null // Kosongkan referensi
    }


    override fun onRestart() {
        super.onRestart()
        // Pasang kembali listener jika activity di-restart (misalnya kembali dari background)
        // Pastikan chatId dan messagesRef sudah diinisialisasi
        if (chatId != null && ::messagesRef.isInitialized) {
            attachMessagesListener()
        } else {
            // Mungkin perlu inisialisasi ulang atau finish jika data penting hilang
            Log.e(TAG, "onRestart: chatId or messagesRef not initialized. Cannot attach listener.")
            // finish() // Atau coba inisialisasi ulang data yang dibutuhkan
        }
    }
}