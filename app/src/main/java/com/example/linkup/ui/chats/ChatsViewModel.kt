package com.example.linkup.ui.chats

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class ChatsViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun getChatsReference() = database.getReference("chats")

    fun getUsersReference() = database.getReference("users")
}