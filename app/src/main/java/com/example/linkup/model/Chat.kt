package com.example.linkup.model

import com.google.firebase.database.Exclude // Untuk field transient jika perlu

data class Chat(
    @get:Exclude // Agar tidak disimpan/dibaca langsung dari Firebase oleh nama ini
    var id: String? = null, // ID unik chat (Firebase key)
    val participants: Map<String, Boolean> = mapOf(), // Map<UserId, true>
    val lastMessage: String? = null,
    val lastMessageTime: Long = 0,
    val lastMessageSenderId: String? = null,
    val lastMessageStatus: String? = null, // "sent", "delivered", "read"
    val createdBy: String? = null, // User yang memulai chat
    val createdAt: Long = System.currentTimeMillis(),

    // Field ini akan diisi secara manual di ChatsFragment setelah mengambil data user lain
    @get:Exclude
    var recipientName: String? = null,
    @get:Exclude
    var recipientProfileImage: String? = null,

    // Untuk grup chat, mungkin ada nama grup dan gambar grup
    val groupName: String? = null,
    val groupImage: String? = null,
    val isGroupChat: Boolean = false,

    // Untuk status 'read' per partisipan
    val readBy: Map<String, Boolean>? = null // Map<UserId, isRead>
) {
    // Kosongkan konstruktor tanpa argumen untuk Firebase
    constructor() : this(null, mapOf(), null, 0, null, null, null, 0, null, null, null, null, false, null)

    // Fungsi helper (opsional, bisa juga di Fragment/Adapter)
    // Untuk mendapatkan nama chat yang akan ditampilkan (bisa nama user lain atau nama grup)
    fun getChatNameForDisplay(currentUserId: String?): String {
        return if (isGroupChat) {
            groupName ?: "Group Chat"
        } else {
            recipientName ?: "Chat" // recipientName diisi oleh Fragment
        }
    }

    fun getChatImageForDisplay(): String? {
        return if (isGroupChat) {
            groupImage
        } else {
            recipientProfileImage // recipientProfileImage diisi oleh Fragment
        }
    }
}