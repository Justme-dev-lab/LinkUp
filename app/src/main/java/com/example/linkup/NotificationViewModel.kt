package com.example.linkup // Sesuaikan package Anda

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.linkup.model.NotificationSettings // Anda perlu membuat data class ini
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class NotificationViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance() // Tambahkan URL jika perlu
    private var currentUserId: String? = auth.currentUser?.uid

    private val _settings = MutableLiveData<NotificationSettings>()
    val settings: LiveData<NotificationSettings> = _settings

    private val _saveStatus = MutableLiveData<String>()
    val saveStatus: LiveData<String> = _saveStatus

    init {
        loadNotificationSettings()
    }

    private fun loadNotificationSettings() {
        currentUserId?.let { userId ->
            database.getReference("users").child(userId).child("settings").child("notifications")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val loadedSettings = snapshot.getValue(NotificationSettings::class.java)
                        _settings.postValue(loadedSettings ?: NotificationSettings()) // Default jika null
                    }

                    override fun onCancelled(error: DatabaseError) {
                        _settings.postValue(NotificationSettings()) // Default on error
                        _saveStatus.postValue("Failed to load settings: ${error.message}")
                    }
                })
        } ?: _settings.postValue(NotificationSettings())
    }

    fun updateNotificationTone(uri: Uri?, name: String?, context: Context) {
        val currentSettings = _settings.value ?: NotificationSettings()
        currentSettings.toneUri = uri?.toString()
        // Jika nama null tapi URI ada, coba dapatkan nama dari URI
        currentSettings.toneName = name ?: uri?.let { RingtoneManager.getRingtone(context, it).getTitle(context) } ?: "Default"
        saveSettings(currentSettings)
    }

    fun setVibrateEnabled(enabled: Boolean) {
        val currentSettings = _settings.value ?: NotificationSettings()
        currentSettings.vibrate = enabled
        saveSettings(currentSettings)
    }

    fun setSoundEnabled(enabled: Boolean) {
        val currentSettings = _settings.value ?: NotificationSettings()
        currentSettings.soundEnabled = enabled
        saveSettings(currentSettings)
    }

    private fun saveSettings(newSettings: NotificationSettings) {
        currentUserId?.let { userId ->
            database.getReference("users").child(userId).child("settings").child("notifications")
                .setValue(newSettings)
                .addOnSuccessListener {
                    _settings.postValue(newSettings) // Update LiveData setelah berhasil save
                    _saveStatus.postValue("Settings saved.")
                }
                .addOnFailureListener {
                    _saveStatus.postValue("Failed to save settings: ${it.message}")
                    // Pertimbangkan untuk memuat ulang setting dari DB jika gagal
                }
        } ?: _saveStatus.postValue("User not logged in.")
    }

    // Helper untuk mendapatkan nama nada dering dari Uri
    fun getRingtoneName(context: Context, uri: Uri?): String {
        if (uri == null) return "Default"
        return try {
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone.getTitle(context)
        } catch (e: Exception) {
            "Custom Tone" // Fallback jika nama tidak bisa didapatkan
        }
    }
}