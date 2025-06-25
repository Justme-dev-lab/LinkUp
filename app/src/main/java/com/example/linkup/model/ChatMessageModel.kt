package com.example.linkup.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class ChatMessageModel(
    var messageId: String = "",
    var sender: String = "",
    var receiver: String = "",
    var message: String = "", // Untuk pesan teks, atau placeholder untuk tipe lain
    var timestamp: Long = 0L,
    var isseen: Boolean = false,
    var type: String = "text",    // "text", "image", "audio", "file", "sound"
    var fileUrl: String? = null,   // URL untuk image, audio (non-soundboard), file
    var soundTitle: String? = null,
    var fileName: String? = null // Judul suara dari soundboard
) {
    // Konstruktor tanpa argumen untuk Firebase
    constructor() : this("", "", "", "", 0L, false, "text", null, null, null)
}