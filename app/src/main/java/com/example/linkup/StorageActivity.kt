package com.example.linkup // Sesuaikan package

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.linkup.adapter.ChatStorageAdapter // Pastikan Anda membuat adapter ini
import com.example.linkup.databinding.ActivityStorageBinding
// import com.example.linkup.chat.ManageChatMediaActivity // Untuk mengelola media chat tertentu

class StorageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStorageBinding
    private val viewModel: StorageViewModel by viewModels()
    private lateinit var chatStorageAdapter: ChatStorageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStorageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topBarLayout.topBarTitle.text = "Storage"
        binding.topBarLayout.backButton.setOnClickListener { finish() }

        setupRecyclerView()
        observeViewModel()

        viewModel.loadStorageDetails() // Muat info penyimpanan perangkat
        viewModel.loadChatStorageDetails() // Muat info penyimpanan per chat

        binding.buttonManageStorage.setOnClickListener {
            // RecyclerView sudah terlihat, tombol ini bisa untuk refresh atau fungsi lain
            // Atau jika Anda ingin membukanya di layar terpisah:
            // Intent(this, ManageAllChatsStorageActivity::class.java).also { startActivity(it) }
            Toast.makeText(this, "Refreshing chat storage list...", Toast.LENGTH_SHORT).show()
            viewModel.loadChatStorageDetails()
        }

        binding.buttonArchiveChats.setOnClickListener {
            // Toast.makeText(this, "Archive Chats clicked - Implement selection logic", Toast.LENGTH_LONG).show()
            // Mengganti Toast dengan Intent untuk membuka Activity baru
            val intent = Intent(this, SelectChatsArchiveActivity::class.java)
            // Anda mungkin ingin meneruskan data ke SelectChatsToArchiveActivity,
            // misalnya, daftar chat ID yang ada untuk memudahkan pemilihan.
            // intent.putStringArrayListExtra("EXISTING_CHAT_IDS", ArrayList(viewModel.chatStorageList.value?.map { it.chatId } ?: listOf()))
            startActivity(intent)
        }
    }

    private fun setupRecyclerView() {
        chatStorageAdapter = ChatStorageAdapter { chatDetail ->
            // Klik pada item chat, buka rincian penyimpanan chat tersebut
            Toast.makeText(this, "Clicked on ${chatDetail.chatName}. Used: ${chatDetail.storageUsedFormatted}", Toast.LENGTH_SHORT).show()
            // Intent(this, ManageChatMediaActivity::class.java).apply {
            //    putExtra("CHAT_ID", chatDetail.chatId)
            //    putExtra("CHAT_NAME", chatDetail.chatName)
            // }.also { startActivity(it) }
        }
        binding.recyclerViewChatStorage.apply { // Pastikan ID ini ada di XML dan tidak dikomentari
            adapter = chatStorageAdapter
            layoutManager = LinearLayoutManager(this@StorageActivity)
            setHasFixedSize(true) // Jika ukuran item tidak berubah
        }
    }

    private fun observeViewModel() {
        viewModel.storageInfo.observe(this) { info ->
            binding.textViewAppStorage.text = "LinkUp: ${info.linkupAppUsedStorageFormatted} (${info.linkupAppStoragePercentage}%)"
            binding.textViewOtherAppsStorage.text = "Other Apps: ${info.otherAppsUsedStorageFormatted} (${info.otherAppsStoragePercentage}%)"
            binding.textViewFreeStorage.text = "Free: ${info.freeDeviceStorageFormatted} (${info.freeStoragePercentage}%)"

            // Update ProgressBar
            binding.progressBarDeviceStorage.max = 100 // Total percentage
            binding.progressBarDeviceStorage.progress = info.linkupAppStoragePercentage
            // Set secondaryProgress untuk menunjukkan LinkUp + OtherApps
            // Bagian OtherApps akan muncul setelah LinkUp
            binding.progressBarDeviceStorage.secondaryProgress = (info.linkupAppStoragePercentage + info.otherAppsStoragePercentage).coerceAtMost(100)
        }

        viewModel.chatStorageList.observe(this) { chats ->
            if (chats.isEmpty()) {
                binding.textViewChatsTitle.text = "No chat storage usage found" // Atau sembunyikan
            } else {
                binding.textViewChatsTitle.text = "Chats Storage Details"
            }
            chatStorageAdapter.submitList(chats)
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBarLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
            // Nonaktifkan tombol saat loading jika perlu
            binding.buttonManageStorage.isEnabled = !isLoading
            binding.buttonArchiveChats.isEnabled = !isLoading
        }
    }
}