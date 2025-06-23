package com.example.linkup.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties // Berguna jika ada field di Firebase yang tidak ada di model Anda
data class Users(
    var uid: String = "", // Properti 'uid' sekarang publik dan bisa diubah (var)
    var username: String = "",
    var profile: String = "",
    var search: String = ""
    // Anda bisa menambahkan field lain di sini jika perlu, misalnya:
    // var email: String = ""
) {
    // Konstruktor tanpa argumen secara otomatis dibuat oleh Kotlin
    // karena semua properti di konstruktor utama memiliki nilai default.
    // Firebase memerlukan konstruktor ini untuk deserialisasi data.
}