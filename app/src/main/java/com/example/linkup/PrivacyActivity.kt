package com.example.linkup // Sesuaikan package Anda

import android.os.Bundle
// HAPUS impor ini jika ada dan tidak digunakan
// import androidx.compose.ui.semantics.text
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.linkup.databinding.ActivityPrivacyBinding // Pastikan ViewBinding diaktifkan

class PrivacyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivacyBinding
    private val viewModel: PrivacyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivacyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Top Bar menggunakan View Binding dari layout yang di-include
        // Asumsi topBarLayout di activity_privacy.xml memiliki ID dan
        // top_bar_layout.xml memiliki view dengan ID topBarTitle dan backButton
        binding.topBarLayout.topBarTitle.text = "Privacy" // Akses TextView via binding
        binding.topBarLayout.backButton.setOnClickListener { finish() } // Akses ImageButton via binding

        viewModel.readReceiptsEnabled.observe(this) { isEnabled ->
            binding.switchReadReceipts.isChecked = isEnabled
        }

        viewModel.saveStatus.observe(this) { status ->
            Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
        }

        binding.switchReadReceipts.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setReadReceiptsEnabled(isChecked)
        }
    }
}