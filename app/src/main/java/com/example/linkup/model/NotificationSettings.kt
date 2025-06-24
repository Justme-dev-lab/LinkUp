package com.example.linkup.model // Pastikan package ini benar

import android.media.RingtoneManager
import android.net.Uri

data class NotificationSettings(
    var toneUri: String? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.toString(),
    var toneName: String? = "Default",
    var vibrate: Boolean = false, // Default getar mati
    var soundEnabled: Boolean = true // Default suara hidup
)