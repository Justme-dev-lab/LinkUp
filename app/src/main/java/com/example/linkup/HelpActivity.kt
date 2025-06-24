package com.example.linkup // Sesuaikan package

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.linkup.databinding.ActivityHelpBinding
// import com.example.linkup.chat.ChatActivity // Asumsi Anda punya ChatActivity

class HelpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpBinding
    private val viewModel: HelpViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topBarLayout.topBarTitle.text = "Help"
        binding.topBarLayout.backButton.setOnClickListener { finish() }

        binding.cardContactUs.setOnClickListener {
            // Buka chat dengan Admin
            // Anda memerlukan ID pengguna Admin dan ChatActivity yang bisa menerima ID target
            val adminId = viewModel.adminUserId // Ambil ID Admin dari ViewModel
            if (adminId != "ID_ADMIN_FIREBASE_ANDA" && adminId.isNotBlank()) {
                // Ganti ChatActivity::class.java dengan Activity chat Anda yang sebenarnya
                // dan pastikan ChatActivity bisa handle intent dengan "USER_ID"
                // Contoh:
                // Intent(this, ChatActivity::class.java).apply {
                //    putExtra("USER_ID", adminId) // Atau nama extra yang digunakan ChatActivity
                //    putExtra("USER_NAME", "Customer Support") // Nama yang ditampilkan di chat
                // }.also { startActivity(it) }
                android.widget.Toast.makeText(this, "Opening chat with Admin (ID: $adminId)", android.widget.Toast.LENGTH_LONG).show()
            } else {
                android.widget.Toast.makeText(this, "Admin contact not configured.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        binding.cardAppInfo.setOnClickListener {
            // Buka InfoAplikasiActivity
            Intent(this, InfoAplikasiActivity::class.java).also { startActivity(it) }
        }
    }
}