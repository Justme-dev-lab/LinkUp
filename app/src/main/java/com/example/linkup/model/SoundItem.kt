package com.example.linkup.model

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties // Abaikan properti tambahan dari Firebase saat deserialisasi
data class SoundItem(
    var id: String = "", // ID unik dari Firebase (key)
    var title: String = "",
    var soundUrl: String = "",
//    var iconName: String = "ic_soundboards", // Default icon
//    var backgroundColor: String = "#D3D3D3", // Default background color
    @get:Exclude @set:Exclude var isPlayingUi: Boolean = false // Status untuk UI, tidak disimpan di Firebase
)