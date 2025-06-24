package com.example.linkup // Sesuaikan package Anda

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class AccountViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance() // Tambahkan URL jika perlu

    private val _userInfo = MutableLiveData<Pair<String?, String?>>() // Pair: Email, Username
    val userInfo: LiveData<Pair<String?, String?>> = _userInfo

    private val _actionFeedback = MutableLiveData<String>()
    val actionFeedback: LiveData<String> = _actionFeedback

    private val _navigateToLogin = MutableLiveData<Boolean>()
    val navigateToLogin: LiveData<Boolean> = _navigateToLogin


    fun loadUserInfo() {
        val currentUser = auth.currentUser
        val email = currentUser?.email
        val userId = currentUser?.uid

        if (userId != null) {
            database.getReference("users").child(userId).child("username")
                .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        val username = snapshot.getValue(String::class.java)
                        _userInfo.postValue(Pair(email, username ?: "N/A"))
                    }

                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        _userInfo.postValue(Pair(email, "Error loading username"))
                    }
                })
        } else {
            _userInfo.postValue(Pair("Not logged in", "Not logged in"))
        }
    }

    fun changePassword(email: String) {
        if (email.isNotEmpty()) {
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        _actionFeedback.postValue("Password reset email sent to $email")
                    } else {
                        _actionFeedback.postValue("Failed to send reset email: ${task.exception?.message}")
                    }
                }
        } else {
            _actionFeedback.postValue("Email address is not available.")
        }
    }

    fun logoutUser() {
        auth.signOut()
        _navigateToLogin.postValue(true)
    }

    fun deleteUserAccount() {
        val user = auth.currentUser
        val userId = user?.uid

        user?.delete()
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Juga hapus data pengguna dari Realtime Database jika perlu
                    if (userId != null) {
                        database.getReference("users").child(userId).removeValue()
                        // Tambahkan penghapusan data lain yang terkait pengguna
                    }
                    _actionFeedback.postValue("Account deleted successfully.")
                    _navigateToLogin.postValue(true) // Arahkan ke login setelah delete
                } else {
                    _actionFeedback.postValue("Failed to delete account: ${task.exception?.message}")
                }
            }
    }

    fun onNavigatedToLogin() {
        _navigateToLogin.value = false
    }
}