package com.example.linkup

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels // Import ini
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.linkup.adapter.SelectableChatAdapter
import com.example.linkup.databinding.ActivitySelectChatsToArchiveBinding
import com.example.linkup.model.SelectableChatItem
import com.example.linkup.ui.archive.SelectChatsArchiveViewModel // Sesuaikan package jika perlu

class SelectChatsArchiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySelectChatsToArchiveBinding
    private val viewModel: SelectChatsArchiveViewModel by viewModels() // Gunakan viewModels delegate
    private lateinit var selectableChatAdapter: SelectableChatAdapter
    private val currentSelectedItems = mutableListOf<SelectableChatItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectChatsToArchiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Toolbar
        setSupportActionBar(binding.toolbarSelectArchive)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Select Chats to Archive"

        setupRecyclerView()
        observeViewModel()

        binding.buttonConfirmArchive.setOnClickListener {
            val selectedToArchive = viewModel.chatList.value?.filter { it.isSelected } ?: emptyList()
            if (selectedToArchive.isNotEmpty()) {
                viewModel.archiveSelectedChats(selectedToArchive)
            } else {
                Toast.makeText(this, "Please select at least one chat to archive.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerView() {
        selectableChatAdapter = SelectableChatAdapter { selectedItem ->
            // Toggle status pilihan item
            val index = viewModel.chatList.value?.indexOfFirst { it.chatId == selectedItem.chatId }
            if (index != null && index != -1) {
                val currentList = viewModel.chatList.value?.toMutableList()
                currentList?.get(index)?.isSelected = !(currentList?.get(index)?.isSelected ?: false)
                // Perbarui list di adapter
                // Ini cara sederhana, idealnya ViewModel yang mengelola state `isSelected`
                // dan LiveData yang memicu update. Namun untuk toggle cepat, ini bisa diterima.
                // Untuk pendekatan yang lebih baik, lihat poin "Manajemen State Pilihan yang Lebih Baik" di bawah.
                selectableChatAdapter.submitList(currentList) // Re-submit list agar DiffUtil bekerja
                selectableChatAdapter.notifyItemChanged(index) // Atau lebih spesifik
            }
        }

        binding.recyclerViewSelectChats.apply {
            adapter = selectableChatAdapter
            layoutManager = LinearLayoutManager(this@SelectChatsArchiveActivity)
            setHasFixedSize(true)
        }
    }

    private fun observeViewModel() {
        viewModel.chatList.observe(this) { chats ->
            selectableChatAdapter.submitList(chats)
            if (chats.isEmpty() && !viewModel.isLoading.value!!){ // Hanya tampilkan jika tidak loading dan kosong
                binding.textViewInstructions.text = "No chats available to archive."
            } else if (!viewModel.isLoading.value!!) {
                binding.textViewInstructions.text = "Select chats you want to archive."
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            // Anda bisa menambahkan ProgressBar di XML dan menampilkannya di sini
            binding.buttonConfirmArchive.isEnabled = !isLoading
            // binding.progressBarSelectArchive.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (isLoading) {
                binding.textViewInstructions.text = "Loading chats..."
            }
        }

        viewModel.toastMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.onToastMessageShown() // Reset pesan agar tidak muncul lagi saat rotasi
                if (it.contains("successfully")) { // Jika berhasil, bisa juga finish activity
                    // finish() // Opsional: tutup activity setelah berhasil arsip
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}