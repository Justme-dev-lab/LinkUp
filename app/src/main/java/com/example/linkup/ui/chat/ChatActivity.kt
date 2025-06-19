package com.example.linkup.ui.chat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.linkup.databinding.ActivityChatBinding
import com.example.linkup.model.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class ChatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatBinding
    private lateinit var chatId: String
    private lateinit var recipientId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatId = intent.getStringExtra("chatId") ?: ""
        recipientId = intent.getStringExtra("recipientId") ?: ""
        val recipientName = intent.getStringExtra("recipientName") ?: ""

        supportActionBar?.title = recipientName

        setupMessageRecyclerView()
        setupSendButton()
        loadMessages()
    }

    private fun setupMessageRecyclerView() {
        // Similar to chat list adapter but for messages
    }

    private fun setupSendButton() {
        binding.sendButton.setOnClickListener {
            val messageText = binding.messageEditText.text.toString()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
                binding.messageEditText.text.clear()
            }
        }
    }

    private fun sendMessage(text: String) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val message = Message(
            senderId = currentUserId,
            text = text,
            timestamp = System.currentTimeMillis(),
            type = "text",
            status = "sent"
        )

        val messageRef = FirebaseDatabase.getInstance().getReference("messages")
            .child(chatId)
            .push()

        messageRef.setValue(message)

        // Update last message in chat
        FirebaseDatabase.getInstance().getReference("chats")
            .child(chatId)
            .updateChildren(mapOf(
                "lastMessage" to text,
                "lastMessageTime" to message.timestamp,
                "lastMessageSender" to currentUserId,
                "lastMessageStatus" to "sent"
            ))
    }

    private fun loadMessages() {
        FirebaseDatabase.getInstance().getReference("messages")
            .child(chatId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val messages = mutableListOf<Message>()
                    for (data in snapshot.children) {
                        val message = data.getValue(Message::class.java)
                        message?.let { messages.add(it) }
                    }
                    // Update recyclerview
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error
                }
            })
    }
}