package com.example.linkup

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.linkup.model.Chat
import com.example.linkup.model.Users
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class AddFriendActivity : AppCompatActivity() {

    private lateinit var searchViewUsers: SearchView
    private lateinit var usersRecyclerView: RecyclerView
    private lateinit var userSearchAdapter: UserSearchAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var textViewNoResults: TextView

    private val usersReference = FirebaseDatabase.getInstance("https://linkup-3b210-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("users")
    private var currentUserId: String? = null

    companion object {
        const val TAG = "AddFriendActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_friend)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        searchViewUsers = findViewById(R.id.searchViewUsers)
        usersRecyclerView = findViewById(R.id.usersRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        textViewNoResults = findViewById(R.id.textViewNoResults)

        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }

        setupRecyclerView()
        setupSearchView()
    }

    private fun setupRecyclerView() {
        userSearchAdapter = UserSearchAdapter { selectedUser ->
            showStartChatConfirmationDialog(selectedUser)
        }
        usersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AddFriendActivity)
            adapter = userSearchAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupSearchView() {
        searchViewUsers.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    searchUsers(query)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrBlank()) {
                    userSearchAdapter.submitList(emptyList())
                    textViewNoResults.visibility = View.GONE
                } else {
                    // Anda bisa menambahkan logika untuk mencari saat teks berubah,
                    // mungkin dengan debounce untuk menghindari terlalu banyak query.
                    // Untuk sekarang, kita hanya mencari saat submit.
                    // Jika ingin mencari saat teks berubah: searchUsers(newText)
                }
                return true
            }
        })
    }

    private fun searchUsers(queryText: String) {
        progressBar.visibility = View.VISIBLE
        textViewNoResults.visibility = View.GONE
        userSearchAdapter.submitList(emptyList()) // Kosongkan hasil sebelumnya

        val searchQuery = queryText.lowercase().trim()

        // Mencari berdasarkan field 'search' (lowercase username)
        // Firebase Realtime Database memiliki keterbatasan dalam query teks kompleks.
        // Query ini akan mencari username yang DIAWALI dengan searchQuery.
        // Jika Anda ingin pencarian yang lebih fleksibel (misalnya, mengandung kata kunci),
        // Anda mungkin perlu mengambil semua data dan memfilternya di client (tidak efisien untuk data besar)
        // atau menggunakan layanan pencarian pihak ketiga seperti Algolia dengan Firebase.
        usersReference.orderByChild("search")
            .startAt(searchQuery)
            .endAt(searchQuery + "\uf8ff") // \uf8ff adalah karakter unicode yang sangat tinggi, digunakan untuk range query
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    progressBar.visibility = View.GONE
                    val foundUsers = mutableListOf<Users>()
                    if (snapshot.exists()) {
                        for (userSnapshot in snapshot.children) {
                            try {
                                // ...
                                val user = userSnapshot.getValue(Users::class.java)
                                if (user != null && user.uid != currentUserId) { // Akses user.uid langsung
                                    // user.uid = userSnapshot.key ?: "" // Tidak perlu lagi jika UID disimpan dengan benar
                                    // atau jika Anda memastikan UID diisi saat membuat objek
                                    foundUsers.add(user)
                                }
                                // ...
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing user data: ${userSnapshot.key}", e)
                            }
                        }
                    }

                    if (foundUsers.isEmpty()) {
                        textViewNoResults.visibility = View.VISIBLE
                    } else {
                        textViewNoResults.visibility = View.GONE
                    }
                    userSearchAdapter.submitList(foundUsers)
                }

                override fun onCancelled(error: DatabaseError) {
                    progressBar.visibility = View.GONE
                    textViewNoResults.visibility = View.VISIBLE
                    Log.e(TAG, "Firebase user search cancelled: ${error.message}")
                    Toast.makeText(this@AddFriendActivity, "Gagal mencari pengguna: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun showStartChatConfirmationDialog(user: Users) {
        AlertDialog.Builder(this)
            .setTitle("Mulai Chat")
            // ...
            .setMessage("Mulai chatting dengan \"${user.username}\"?") // Akses user.username
            // ...
            .setPositiveButton("Iya") { dialog, _ ->
                initiateChatWithUser(user)
                dialog.dismiss()
            }
            .setNegativeButton("Batalkan") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun initiateChatWithUser(selectedUser: Users) {
        // ...
        val selectedUserId = selectedUser.uid // Akses user.uid
        // ...
        if (currentUserId == null || selectedUserId == currentUserId) {
            Toast.makeText(this, "Tidak bisa memulai chat dengan diri sendiri.", Toast.LENGTH_SHORT).show()
            return
        }

        // Logika untuk membuat atau membuka chat yang sudah ada:
        // 1. Cek apakah chat antara currentUser dan selectedUser sudah ada.
        //    Cara paling sederhana adalah membuat ID chat yang konsisten, misal:
        //    userId1_userId2 (dengan userId1 < userId2 secara leksikografis)
        // 2. Jika ada, langsung buka.
        // 3. Jika tidak ada, buat node chat baru.

        val chatParticipants = mapOf(
            currentUserId!! to true,
            selectedUserId to true
        )

        // Cek apakah chat sudah ada (ini bagian yang kompleks, kita sederhanakan dulu)
        // Kita akan langsung membuat chat baru untuk contoh ini, atau jika sudah ada,
        // akan menimpanya jika ID chatnya sama. Idealnya, Anda perlu query yang lebih baik.

        // Untuk sekarang, kita anggap setiap "Iya" akan membuat/memastikan chat ada.
        val chatsRef = FirebaseDatabase.getInstance("https://linkup-3b210-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("chats")

        // Buat ID chat yang unik dan konsisten (contoh sederhana)
        val chatRoomId = if (currentUserId!! < selectedUserId) {
            "${currentUserId}_${selectedUserId}"
        } else {
            "${selectedUserId}_${currentUserId}"
        }

        val newChat = Chat(
            id = chatRoomId,
            participants = chatParticipants,
            lastMessage = "Ayo mulai mengobrol!", // Pesan awal
            lastMessageTime = System.currentTimeMillis(),
            lastMessageSenderId = currentUserId, // atau null jika belum ada pesan
            createdBy = currentUserId,
            createdAt = System.currentTimeMillis(),
            isGroupChat = false,
            // recipientName dan recipientProfileImage akan di-load oleh ChatAdapter/ChatsFragment
        )

        chatsRef.child(chatRoomId).setValue(newChat)
            .addOnSuccessListener {
                Toast.makeText(this, "Teman berhasil ditambahkan! Chat dimulai.", Toast.LENGTH_LONG).show()
                // Kembali ke MainActivity (yang akan menampilkan ChatsFragment)
                // dan pastikan ChatsFragment diperbarui.
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish() // Tutup AddFriendActivity
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memulai chat: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Failed to initiate chat", e)
            }
    }
}