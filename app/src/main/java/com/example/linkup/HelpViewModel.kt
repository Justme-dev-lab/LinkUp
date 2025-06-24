package com.example.linkup // Sesuaikan package

import androidx.lifecycle.ViewModel

class HelpViewModel : ViewModel() {
    // Logika untuk mendapatkan ID admin mungkin bisa ditaruh di sini jika diambil dari remote config/DB
    val adminUserId: String = "ID_ADMIN_FIREBASE_ANDA" // Ganti dengan ID user admin yang sebenarnya
}