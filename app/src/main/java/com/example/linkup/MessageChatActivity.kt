package com.example.linkup

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.linkup.databinding.ActivityMessageChatBinding // Ganti dengan nama file binding Anda
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import de.hdodenhof.circleimageview.CircleImageView

class MessageChatActivity : AppCompatActivity() {

    // View Binding
    private lateinit var binding: ActivityMessageChatBinding

    private var firebaseUser: FirebaseUser? = null
    private var recipientUserId: String? = null // ID pengguna penerima pesan
    private var recipientUserName: String? = null
    private var recipientProfileImageUrl: String? = null

    // Untuk RecyclerView pesan (perlu adapter dan model data pesan nanti)
    // private lateinit var messageAdapter: MessageAdapter
    // private var messagesList: MutableList<ChatMessageModel> = mutableListOf() // Ganti dengan model pesan Anda

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

        // Dapatkan data dari Intent
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

        // Setup Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // Sembunyikan judul default jika Anda menggunakan TextView kustom
        // binding.toolbar.setNavigationOnClickListener { finish() } // Tambahkan tombol kembali jika perlu

        binding.usernameMchat.text = recipientUserName ?: "User" // Tampilkan nama pengguna penerima
        if (!recipientProfileImageUrl.isNullOrEmpty()) {
            // Asumsi CircleImageView di toolbar memiliki ID profileImageMchat (sesuaikan jika beda)
            // Anda perlu menambahkan ID ke CircleImageView di dalam Toolbar pada XML Anda
            // Contoh ID: android:id="@+id/profile_image_mchat"
            val profileImageViewInToolbar: CircleImageView? = binding.toolbar.findViewById(R.id.profile_image_mchat) // Cari view di dalam toolbar
            profileImageViewInToolbar?.let {
                Glide.with(this)
                    .load(recipientProfileImageUrl)
                    .placeholder(R.drawable.profile) // Placeholder default
                    .into(it)
            }
        } else {
            val profileImageViewInToolbar: CircleImageView? = binding.toolbar.findViewById(R.id.profile_image_mchat)
            profileImageViewInToolbar?.setImageResource(R.drawable.profile)
        }


        // Setup RecyclerView untuk pesan
        // binding.recycleViewChats.layoutManager = LinearLayoutManager(this).apply {
        //     stackFromEnd = true // Pesan baru muncul di bawah
        // }
        // messageAdapter = MessageAdapter(this, messagesList, firebaseUser!!.uid) // Perlu dibuat
        // binding.recycleViewChats.adapter = messageAdapter

        // Tombol Kirim Pesan
        binding.sendMessageBtn.setOnClickListener {
            val messageText: String = binding.textMessage.text.toString().trim()
            if (messageText.isEmpty()) {
                Toast.makeText(this@MessageChatActivity, "Please write a message", Toast.LENGTH_SHORT).show()
            } else {
                // firebaseUser sudah pasti tidak null karena ada pengecekan di atas
                // recipientUserId juga sudah pasti tidak null
                sendMessageToUser(firebaseUser!!.uid, recipientUserId!!, messageText)
                binding.textMessage.setText("") // Kosongkan input field setelah mengirim
            }
        }

        // Tombol Attach File (implementasi nanti)
        binding.attachImageFileBtn.setOnClickListener {
            Toast.makeText(this, "Attach file clicked (Not implemented)", Toast.LENGTH_SHORT).show()
        }

        // Load pesan yang sudah ada (implementasi nanti)
        // loadMessages(firebaseUser!!.uid, recipientUserId!!)
    }

    private fun sendMessageToUser(senderId: String, receiverId: String, message: String) {
        val databaseReference: DatabaseReference = FirebaseDatabase.getInstance().reference

        val messageData = HashMap<String, Any>()
        messageData["sender"] = senderId
        messageData["receiver"] = receiverId
        messageData["message"] = message
        messageData["timestamp"] = System.currentTimeMillis()
        // messageData["isseen"] = false // Jika Anda ingin fitur read receipt

        // Buat path untuk menyimpan pesan, misal /messages/{chatId}/<messageId>
        // Cara sederhana: path berdasarkan kombinasi ID pengirim dan penerima yang diurutkan
        val chatId = if (senderId < receiverId) "$senderId-$receiverId" else "$receiverId-$senderId"
        val messagePath = "messages/$chatId"

        databaseReference.child(messagePath).push().setValue(messageData)
            .addOnSuccessListener {
                Log.d(TAG, "Message sent successfully.")
                // Update node 'chats' untuk daftar chat (lastMessage, lastMessageTime)
                updateChatListNode(senderId, receiverId, message, System.currentTimeMillis())
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to send message: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Failed to send message", e)
            }
    }

    private fun updateChatListNode(user1Id: String, user2Id: String, lastMessage: String, timestamp: Long) {
        val chatRef = FirebaseDatabase.getInstance().getReference("chats")
        val chatId = if (user1Id < user2Id) "$user1Id-$user2Id" else "$user2Id-$user1Id"

        val chatInfo = mapOf(
            "id" to chatId,
            "lastMessage" to lastMessage,
            "lastMessageTime" to timestamp,
            "participants" to mapOf(
                user1Id to true,
                user2Id to true
            ),
            "isGroupChat" to false // Untuk chat 1-on-1
            // Tambahkan groupName dan groupImage null atau kosong jika perlu konsistensi model
        )

        chatRef.child(chatId).updateChildren(chatInfo)
            .addOnSuccessListener { Log.d(TAG, "Chat list node updated for $chatId") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to update chat list node for $chatId", e) }
    }


    // Fungsi untuk memuat pesan (perlu diimplementasikan)
    // private fun loadMessages(senderId: String, receiverId: String) {
    //     val chatId = if (senderId < receiverId) "$senderId-$receiverId" else "$receiverId-$senderId"
    //     val messagesRef = FirebaseDatabase.getInstance().getReference("messages/$chatId")
    //
    //     messagesRef.addValueEventListener(object : ValueEventListener {
    //         override fun onDataChange(snapshot: DataSnapshot) {
    //             messagesList.clear()
    //             for (data in snapshot.children) {
    //                 val chatMessage = data.getValue(ChatMessageModel::class.java) // Ganti dengan model pesan Anda
    //                 if (chatMessage != null) {
    //                     messagesList.add(chatMessage)
    //                 }
    //             }
    //             messageAdapter.notifyDataSetChanged()
    //             binding.recycleViewChats.scrollToPosition(messagesList.size - 1) // Scroll ke pesan terakhir
    //         }
    //
    //         override fun onCancelled(error: DatabaseError) {
    //             Log.e(TAG, "Failed to load messages.", error.toException())
    //         }
    //     })
    // }
}