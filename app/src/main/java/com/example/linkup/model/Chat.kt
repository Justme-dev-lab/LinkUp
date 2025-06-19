package com.example.linkup.model

data class Chat(
    var id: String? = null,
    val participants: Map<String, Boolean> = emptyMap(),
    val lastMessage: String? = null,
    val lastMessageTime: Long = 0,
    val lastMessageSender: String? = null,
    val lastMessageStatus: String? = null,
    var recipientId: String? = null,
    var recipientName: String? = null,
    var recipientProfileImage: String? = null
)