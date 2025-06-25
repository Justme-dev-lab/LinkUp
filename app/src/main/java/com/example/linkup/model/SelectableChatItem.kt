package com.example.linkup.model // Atau package yang sesuai

data class SelectableChatItem(
    val chatId: String,
    val chatName: String?,
    val profileImageUrl: String?,
    val lastMessage: String?, // Opsional, bisa digunakan untuk tampilan
    var isSelected: Boolean = false,
    val isCurrentlyArchived: Boolean // Untuk mengetahui status arsip awal
)