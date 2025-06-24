package com.example.linkup // Sesuaikan package Anda

import android.content.Intent
import android.os.Bundle
// HAPUS impor ini karena tidak digunakan dan menyebabkan error 'compose'
// import androidx.compose.ui.semantics.text
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.linkup.databinding.ActivityAccountBinding // Pastikan ViewBinding diaktifkan
// Jika LoginActivity ada di package yang sama, impor ini tidak eksplisit diperlukan, tapi tidak masalah.
// import com.example.linkup.LoginActivity

class AccountActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountBinding
    private val viewModel: AccountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Top Bar menggunakan View Binding dari layout yang di-include
        // Asumsi topBarLayout di activity_account.xml memiliki ID dan
        // top_bar_layout.xml memiliki view dengan ID topBarTitle dan backButton
        // Jika topBarLayout adalah include, Anda akan mengaksesnya seperti ini:
        binding.topBarLayout.topBarTitle.text = "Account" // Akses TextView via binding
        binding.topBarLayout.backButton.setOnClickListener { finish() } // Akses ImageButton via binding

        viewModel.loadUserInfo()

        viewModel.userInfo.observe(this) { (email, username) ->
            binding.textViewEmail.text = "Email: ${email ?: "N/A"}"
            binding.textViewUsername.text = "Username: ${username ?: "N/A"}"
        }

        viewModel.actionFeedback.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }

        viewModel.navigateToLogin.observe(this) { navigate ->
            if (navigate) {
                val intent = Intent(this, LoginActivity::class.java) // Pastikan LoginActivity ada
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finishAffinity() // Tutup semua activity sebelumnya
                viewModel.onNavigatedToLogin() // Reset flag
            }
        }

        binding.buttonChangePassword.setOnClickListener {
            val email = viewModel.userInfo.value?.first
            if (email != null) {
                viewModel.changePassword(email)
            } else {
                Toast.makeText(this, "Email not available to send reset link.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonSwitchAccount.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Switch Account")
                .setMessage("This will log you out. Are you sure?")
                .setPositiveButton("Logout") { _, _ ->
                    viewModel.logoutUser()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.buttonDeleteAccount.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to permanently delete your account? This action cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteUserAccount()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}