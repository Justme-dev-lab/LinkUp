package com.example.linkup

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MessageChatActivity : AppCompatActivity() {



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message_chat)



        send_message_btn.setOnClickListener {
            var message: String = text_message.text.toString()
            if (message == null)
            {
                Toast.makeText(this@MessageChatActivity,"Please write message", Toast.LENGTH_SHORT).show()
            }
            else
            {
                sendmessageToUser(firebaseUser!!.uid, userId, message)
            }
        }
    }
}