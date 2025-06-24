package com.example.linkup // Sesuaikan package Anda

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class PrivacyViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance() // Tambahkan URL jika perlu
    private var currentUserId: String? = auth.currentUser?.uid

    private val _readReceiptsEnabled = MutableLiveData<Boolean>()
    val readReceiptsEnabled: LiveData<Boolean> = _readReceiptsEnabled

    private val _saveStatus = MutableLiveData<String>()
    val saveStatus: LiveData<String> = _saveStatus

    init {
        loadReadReceiptsSetting()
    }

    private fun loadReadReceiptsSetting() {
        currentUserId?.let { userId ->
            // Simpan setting di path users/{userId}/settings/readReceipts
            database.getReference("users").child(userId).child("settings").child("readReceipts")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        // Defaultnya true jika belum ada setting
                        _readReceiptsEnabled.postValue(snapshot.getValue(Boolean::class.java) ?: true)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        _readReceiptsEnabled.postValue(true) // Default on error
                        _saveStatus.postValue("Failed to load setting: ${error.message}")
                    }
                })
        } ?: _readReceiptsEnabled.postValue(true) // Default jika user tidak login
    }

    fun setReadReceiptsEnabled(enabled: Boolean) {
        currentUserId?.let { userId ->
            database.getReference("users").child(userId).child("settings").child("readReceipts")
                .setValue(enabled)
                .addOnSuccessListener {
                    _readReceiptsEnabled.postValue(enabled)
                    _saveStatus.postValue("Setting saved.")
                }
                .addOnFailureListener {
                    _saveStatus.postValue("Failed to save setting: ${it.message}")
                    // Kembalikan ke state sebelumnya jika gagal
                    loadReadReceiptsSetting()
                }
        } ?: _saveStatus.postValue("User not logged in.")
    }
}