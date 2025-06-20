package com.example.linkup.ui.chats

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.linkup.R // Pastikan R diimport
import com.example.linkup.databinding.FragmentChatsBinding
import com.example.linkup.model.Chat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ChatsFragment : Fragment() {

    private var _binding: FragmentChatsBinding? = null
    private val binding get() = _binding!!

    // Hapus chatsViewModel jika tidak digunakan secara aktif untuk data observe di sini
    // private lateinit var chatsViewModel: ChatsViewModel

    private lateinit var chatAdapter: ChatAdapter
    private val chatList = mutableListOf<Chat>() // List untuk adapter

    private lateinit var chatsReference: DatabaseReference
    private var chatsValueEventListener: ValueEventListener? = null
    private var currentUserId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // chatsViewModel = ViewModelProvider(this).get(ChatsViewModel::class.java)
        _binding = FragmentChatsBinding.inflate(inflater, container, false)
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup RecyclerView dan Adapter terlebih dahulu
        setupRecyclerView()

        // Kemudian setup komponen lain yang bergantung pada adapter atau view
        setupSearch()
        setupFloatingButtonClickListener() // Hanya setup listener, FAB sudah di XML

        // Amati insets untuk menyesuaikan padding RecyclerView agar tidak tertutup BottomNav
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { rootView, insets ->
            val bottomNavInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            // Sesuaikan padding bawah RecyclerView agar kontennya tidak tertutup oleh BottomNavigationView
            // dan beri ruang juga untuk FAB
            val fabHeightApproximation = resources.getDimensionPixelSize(R.dimen.fab_size_plus_margin) // Definisikan ini di dimens.xml
            val bottomNavHeight = getBottomNavigationViewHeight() // Fungsi untuk mendapatkan tinggi BottomNav dari Activity

            binding.chatRecyclerView.updatePadding(bottom = bottomNavHeight + fabHeightApproximation) // Beri ruang untuk BottomNav & FAB
            binding.fabNewChat.translationY = -bottomNavHeight.toFloat() // Geser FAB ke atas setinggi BottomNav

            // Kembalikan insets yang tidak dikonsumsi
            insets
        }


        // Load data atau attach listener Firebase
        if (currentUserId != null) {
            attachChatsListener()
        } else {
            Log.w("ChatsFragment", "User not logged in, cannot load chats.")
            // Mungkin tampilkan pesan atau handle kasus user tidak login
        }

        binding.profileButton.setOnClickListener {
            // TODO: Implement navigation to profile screen
        }
    }

    // Fungsi helper untuk mendapatkan tinggi BottomNavigationView dari MainActivity
    // Ini agak tricky karena Fragment tidak secara langsung tahu tentang View di Activity
    // Alternatifnya adalah menggunakan SharedViewModel atau interface callback ke Activity
    private fun getBottomNavigationViewHeight(): Int {
        val mainActivityView = activity?.findViewById<View>(android.R.id.content)
        val bottomNavView = mainActivityView?.rootView?.findViewById<View>(R.id.nav_view) // Asumsi ID nav_view di MainActivity
        return bottomNavView?.height ?: resources.getDimensionPixelSize(R.dimen.default_bottom_nav_height) // Definisikan default_bottom_nav_height
    }


    private fun setupRecyclerView() {
        // Inisialisasi adapter SEBELUM digunakan
        chatAdapter = ChatAdapter(chatList.toString(), currentUserId) { chat -> // Teruskan currentUserId
            // Handle item click, misalnya buka layar chat detail
            // val intent = Intent(requireContext(), ChatDetailActivity::class.java)
            // intent.putExtra("CHAT_ID", chat.id)
            // startActivity(intent)
            Log.d("ChatsFragment", "Chat clicked: ${chat.id}")
        }

        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
            setHasFixedSize(true) // Optimasi jika ukuran item tidak berubah
        }
    }

    private fun attachChatsListener() {
        chatsReference = FirebaseDatabase.getInstance().getReference("chats")
        // Hapus listener lama jika ada sebelum menambahkan yang baru
        chatsValueEventListener?.let { chatsReference.removeEventListener(it) }

        chatsValueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newChats = mutableListOf<Chat>()
                for (data in snapshot.children) {
                    try {
                        val chat = data.getValue(Chat::class.java)
                        chat?.id = data.key // Simpan ID chat dari Firebase key
                        // Logika untuk menentukan recipientName dan recipientProfileImage
                        // perlu ada di sini atau di model Chat, berdasarkan participants
                        chat?.let {
                            // Anda perlu mengisi recipientName dan recipientProfileImage di sini
                            // berdasarkan partisipan lain dalam chat.
                            // Ini contoh sederhana, Anda perlu logika yang lebih baik.
                            val otherParticipantId = it.participants.keys.firstOrNull { key -> key != currentUserId }
                            if (otherParticipantId != null) {
                                // Ambil data user lain dari node "users"
                                FirebaseDatabase.getInstance().getReference("users").child(otherParticipantId)
                                    .addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(userSnapshot: DataSnapshot) {
                                            it.recipientName = userSnapshot.child("username").getValue(String::class.java) ?: "Unknown User"
                                            it.recipientProfileImage = userSnapshot.child("profileImageUrl").getValue(String::class.java) ?: ""

                                            // Setelah semua data chat dan user terisi, tambahkan ke list dan update adapter
                                            // Ini mungkin perlu di-handle dengan lebih baik jika ada banyak chat
                                            // untuk menghindari banyak nested listener
                                            if (!newChats.contains(it)) newChats.add(it) // Hindari duplikasi jika listener user terpanggil berkali-kali
                                            if (newChats.size == snapshot.childrenCount.toInt()) { // Cek jika semua data user sudah diambil
                                                chatAdapter.updateChats(newChats.sortedByDescending { c -> c.lastMessageTime })
                                            }
                                        }
                                        override fun onCancelled(error: DatabaseError) {
                                            Log.e("ChatsFragment", "Failed to load user data for chat: $otherParticipantId", error.toException())
                                            // Tambahkan chat dengan data default jika user tidak ditemukan
                                            it.recipientName = "Unknown User"
                                            if (!newChats.contains(it)) newChats.add(it)
                                            if (newChats.size == snapshot.childrenCount.toInt()) {
                                                chatAdapter.updateChats(newChats.sortedByDescending { c -> c.lastMessageTime })
                                            }
                                        }
                                    })
                            } else {
                                it.recipientName = "Group Chat or Self" // Atau handle kasus ini
                                if (!newChats.contains(it)) newChats.add(it)
                                if (newChats.size == snapshot.childrenCount.toInt()) {
                                    chatAdapter.updateChats(newChats.sortedByDescending { c -> c.lastMessageTime })
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ChatsFragment", "Error parsing chat data: ${data.key}", e)
                    }
                }
                // Pindahkan pembaruan adapter setelah semua data user (jika ada) selesai diambil
                // chatAdapter.updateChats(newChats.sortedByDescending { it.lastMessageTime })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatsFragment", "Firebase chats listener cancelled.", error.toException())
            }
        }

        // Query untuk mendapatkan chat yang melibatkan currentUser
        val query = chatsReference
            .orderByChild("participants/$currentUserId")
            .equalTo(true) // Hanya chat dimana currentUser adalah partisipan

        query.addValueEventListener(chatsValueEventListener!!)
    }


    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (::chatAdapter.isInitialized) { // Pastikan adapter sudah ada
                    chatAdapter.filter.filter(newText)
                }
                return true
            }
        })
    }

    private fun setupFloatingButtonClickListener() {
        // FAB sudah ada di XML, kita hanya perlu set listener-nya
        binding.fabNewChat.setOnClickListener {
            showNewChatDialog()
        }
    }

    private fun showNewChatDialog() {
        // TODO: Implement dialog to select user to chat with
        // Contoh: Navigasi ke fragment atau activity baru untuk memilih kontak
        // findNavController().navigate(R.id.action_chatsFragment_to_selectUserFragment)
        Log.d("ChatsFragment", "FAB New Chat clicked")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Hapus listener Firebase untuk mencegah memory leak
        chatsValueEventListener?.let {
            FirebaseDatabase.getInstance().getReference("chats").removeEventListener(it)
        }
        chatsValueEventListener = null
        _binding = null // Penting untuk ViewBinding di Fragment
    }
}