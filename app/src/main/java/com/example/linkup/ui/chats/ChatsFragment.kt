package com.example.linkup.ui.chats

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.linkup.ProfileActivity
import com.example.linkup.R // Pastikan ini adalah R dari package aplikasi Anda
import com.example.linkup.databinding.FragmentChatsBinding
import com.example.linkup.model.Chat // Pastikan model Chat Anda benar
// Import MainActivity jika Anda akan melakukan cast (activity as? MainActivity)
// import com.example.linkup.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

// Definisikan tipe alias untuk MainActivity jika Anda sering menggunakannya untuk casting
// typealias MainActivityType = com.example.linkup.MainActivity // Ganti dengan path MainActivity Anda

class ChatsFragment : Fragment(), BottomNavHeightListener {


    private var _binding: FragmentChatsBinding? = null
    private val binding get() = _binding!!

    private var currentBottomNavHeight = 0 // Simpan tinggi BottomNav
    private var currentImeHeight = 0 // Simpan tinggi IME

    private lateinit var chatAdapter: ChatAdapter
    // private val chatList = mutableListOf<Chat>() // List untuk adapter, akan di-supply ke adapter

    private lateinit var chatsReference: DatabaseReference
    private var chatsValueEventListener: ValueEventListener? = null
    private var currentUserId: String? = null

    // Interface untuk komunikasi dengan Activity
    interface ActivityCallback {
        fun requestBottomNavHeight(listener: BottomNavHeightListener)
        fun clearBottomNavHeightListener(listener: BottomNavHeightListener)
    }
    private var activityCallback: ActivityCallback? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is ActivityCallback) {
            activityCallback = context
        } else {
            Log.e("ChatsFragment", "$context must implement ActivityCallback")
        }
    }






    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatsBinding.inflate(inflater, container, false)
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViewAdapter() // Inisialisasi adapter dulu
        setupRecyclerViewLayout()
        setupSearch()
        setupFloatingButtonClickListener()
        applyStatusBarPaddingToHeader()

        // Minta Activity untuk menghitung dan mengirim tinggi BottomNav
        activityCallback?.requestBottomNavHeight(this)

        // ChatsFragment.kt -> onViewCreated
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets -> // 'v' adalah view-nya
            Log.d("ChatsFragment", "OnApplyWindowInsetsListener triggered")
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemGestureInsets = insets.getInsets(WindowInsetsCompat.Type.systemGestures())

            // Update currentImeHeight dengan nilai terbaru
            currentImeHeight != imeInsets.bottom
            currentImeHeight = imeInsets.bottom

            // currentBottomNavHeight di sini akan menjadi nilai yang terakhir di-set oleh
            // onBottomNavHeightCalculated atau fallback awal.
            // Kita tidak boleh mengandalkan navigationBarsInsets.bottom sebagai sumber utama
            // jika ActivityCallback digunakan, karena itu bisa berbeda (misal, pada mode edge-to-edge).

            val effectiveBottomNavHeight = currentBottomNavHeight + systemGestureInsets.bottom

            // Panggil adjustViewsForBottomInsets untuk menerapkan perubahan IME atau gesture insets
            adjustViewsForBottomInsets(effectiveBottomNavHeight, currentImeHeight)
            Log.d("ChatsFragment", "Adjusting from OnApplyWindowInsetsListener. EffectiveBNavH: $effectiveBottomNavHeight, IMEH: $currentImeHeight")

            // Kembalikan insets asli agar tidak dikonsumsi sepenuhnya jika tidak perlu
            insets
        }

        if (currentUserId != null) {
            attachChatsListener()
        } else {
            Log.w("ChatsFragment", "User not logged in, cannot load chats.")
        }

        binding.profileButton.setOnClickListener {
            val intent = Intent(requireContext(), ProfileActivity::class.java)
            startActivity(intent)



        }
    }

    // ChatsFragment.kt
    override fun onBottomNavHeightCalculated(height: Int) {
        if (!isAdded || _binding == null) return
        Log.d("ChatsFragment", "onBottomNavHeightCalculated called with height: $height")

        if (height > 0) { // Hanya proses jika tinggi valid
            currentBottomNavHeight != height // Cek apakah tinggi BottomNav benar-benar berubah
            currentBottomNavHeight = height // Selalu update dengan nilai terbaru dari Activity

            // Dapatkan inset gestur saat ini juga, karena bisa saja berubah
            val systemGestureInsetsBottom = view?.let {
                ViewCompat.getRootWindowInsets(it)?.getInsets(WindowInsetsCompat.Type.systemGestures())?.bottom ?: 0
            } ?: 0
            val effectiveBottomNavHeight = currentBottomNavHeight + systemGestureInsetsBottom

            // Panggil adjustViewsForBottomInsets jika tinggi BottomNav berubah,
            // atau jika ini adalah pemanggilan pertama (untuk memastikan UI disesuaikan).
            // Atau jika kita ingin memastikan UI selalu di-refresh saat callback ini dipanggil.
            adjustViewsForBottomInsets(effectiveBottomNavHeight, currentImeHeight)
            Log.d("ChatsFragment", "Adjusting from onBottomNavHeightCalculated. EffectiveBNavH: $effectiveBottomNavHeight, IMEH: $currentImeHeight")
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("ChatsFragment", "onResume called")

        // 1. Minta tinggi BottomNav dari Activity.
        //    Ini akan memicu onBottomNavHeightCalculated jika MainActivity sudah tahu tingginya,
        //    atau memicu pengukuran jika belum.
        activityCallback?.requestBottomNavHeight(this)

        // 2. Minta WindowInsets untuk diterapkan kembali ke root view fragment.
        //    Ini akan memicu ulang lambda di setOnApplyWindowInsetsListener yang sudah Anda setel
        //    di onViewCreated. Listener ini akan mendapatkan IME dan gesture insets terbaru
        //    dan memanggil adjustViewsForBottomInsets.
        view?.let {
            Log.d("ChatsFragment", "Requesting apply insets on resume")
            ViewCompat.requestApplyInsets(it)
        }

        // 3. PENTING: Panggil adjustViewsForBottomInsets secara eksplisit jika nilai-nilai sudah ada.
        // Ini menangani kasus di mana onResume dipanggil, tetapi callback
        // (onBottomNavHeightCalculated atau OnApplyWindowInsetsListener) mungkin tidak
        // langsung dieksekusi atau tidak mengubah nilai yang sudah ada.
        // Ini memastikan bahwa bahkan jika tidak ada perubahan inset/tinggi,
        // posisi FAB dan padding RecyclerView tetap benar berdasarkan nilai yang terakhir diketahui.
        if (isAdded && _binding != null && (currentBottomNavHeight > 0 || currentImeHeight >= 0)) { // Perhatikan >= 0 untuk IME
            Log.d("ChatsFragment", "onResume: Explicitly calling adjustViewsForBottomInsets with BNavH=$currentBottomNavHeight, IMEH=$currentImeHeight")
            val systemGestureInsetsBottom = view?.let {
                ViewCompat.getRootWindowInsets(it)?.getInsets(WindowInsetsCompat.Type.systemGestures())?.bottom ?: 0
            } ?: 0
            val effectiveBottomNavHeight = currentBottomNavHeight + systemGestureInsetsBottom
            adjustViewsForBottomInsets(effectiveBottomNavHeight, currentImeHeight)
        }
    }

    private fun adjustViewsForBottomInsets(bottomNavHeightToUse: Int, imeHeightToUse: Int) {
        if (!isAdded || _binding == null) return

        val fabHeightWithMargin = resources.getDimensionPixelSize(R.dimen.fab_size_plus_margin)
        val fabMarginFromBottomNav = resources.getDimensionPixelSize(R.dimen.fab_margin_from_bottom_nav)

        // Padding untuk RecyclerView:
        // tinggi BottomNav (termasuk gesture bar) + tinggi FAB dengan margin di atasnya + tinggi IME
        val recyclerViewPaddingBottom = bottomNavHeightToUse + fabHeightWithMargin + imeHeightToUse
        binding.chatRecyclerView.updatePadding(bottom = recyclerViewPaddingBottom)

        // Update margin bawah FAB agar berada di atas BottomNav (dan IME jika perlu)
        binding.fabNewChat.updateLayoutParams<ConstraintLayout.LayoutParams> {
            // Margin bawah FAB adalah tinggi BottomNav (termasuk gesture bar) + margin standar FAB dari atas BottomNav + tinggi IME
            bottomMargin = bottomNavHeightToUse + fabMarginFromBottomNav + imeHeightToUse
        }
        // Log.d("ChatsFragment", "Adjusting views: BNavH=$bottomNavHeightToUse, IMEH=$imeHeightToUse, RVPaddingB=$recyclerViewPaddingBottom, FABMarginB=${binding.fabNewChat.layoutParams}")
    }


    private fun applyStatusBarPaddingToHeader() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerTitle) { headerView, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            headerView.updatePadding(top = systemBars.top)
            // Kembalikan insets yang sudah dimodifikasi (hanya mengkonsumsi bagian atas)
            WindowInsetsCompat.Builder(insets).setInsets(
                WindowInsetsCompat.Type.systemBars(),
                androidx.core.graphics.Insets.of(systemBars.left, 0, systemBars.right, systemBars.bottom)
            ).build()
        }
    }

    private fun setupRecyclerViewAdapter() {
        // Inisialisasi adapter
        // Anda perlu memastikan ChatAdapter Anda menerima List<Chat> dan bukan String
        chatAdapter = ChatAdapter(currentUserId) { chat -> // Pastikan konstruktor adapter sesuai
            Log.d("ChatsFragment", "Chat clicked: ${chat.id}")
            // val intent = Intent(requireContext(), ChatDetailActivity::class.java)
            // intent.putExtra("CHAT_ID", chat.id)
            // startActivity(intent)
        }
    }
    private fun setupRecyclerViewLayout() {
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter // Set adapter yang sudah diinisialisasi
            setHasFixedSize(true)
        }
    }


    private fun attachChatsListener() {
        if (currentUserId == null) return
        chatsReference = FirebaseDatabase.getInstance().getReference("chats")
        chatsValueEventListener?.let { chatsReference.removeEventListener(it) } // Hapus listener lama

        chatsValueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newChats = mutableListOf<Chat>()
                val totalChildren = snapshot.childrenCount
                var processedChildren = 0

                if (totalChildren == 0L) {
                    chatAdapter.updateChats(emptyList()) // Update dengan list kosong jika tidak ada chat
                    return
                }

                for (data in snapshot.children) {
                    try {
                        val chat = data.getValue(Chat::class.java)
                        chat?.id = data.key
                        chat?.let { loadedChat ->
                            val otherParticipantId = loadedChat.participants.keys.firstOrNull { key -> key != currentUserId }
                            if (otherParticipantId != null) {
                                FirebaseDatabase.getInstance().getReference("users").child(otherParticipantId)
                                    .addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(userSnapshot: DataSnapshot) {
                                            loadedChat.recipientName = userSnapshot.child("username").getValue(String::class.java) ?: "Unknown User"
                                            loadedChat.recipientProfileImage = userSnapshot.child("profileImageUrl").getValue(String::class.java) ?: ""
                                            synchronized(newChats) { // Sinkronisasi akses ke newChats
                                                newChats.add(loadedChat)
                                            }
                                            processedChildren++
                                            if (processedChildren == totalChildren.toInt()) {
                                                chatAdapter.updateChats(newChats.sortedByDescending { c -> c.lastMessageTime })
                                            }
                                        }
                                        override fun onCancelled(error: DatabaseError) {
                                            Log.e("ChatsFragment", "Failed to load user data for chat: $otherParticipantId", error.toException())
                                            loadedChat.recipientName = "Unknown User" // Default
                                            synchronized(newChats) {
                                                newChats.add(loadedChat)
                                            }
                                            processedChildren++
                                            if (processedChildren == totalChildren.toInt()) {
                                                chatAdapter.updateChats(newChats.sortedByDescending { c -> c.lastMessageTime })
                                            }
                                        }
                                    })
                            } else {
                                loadedChat.recipientName = if (loadedChat.isGroupChat) loadedChat.groupName ?: "Group Chat" else "Self Chat" // Handle grup atau chat sendiri
                                synchronized(newChats) {
                                    newChats.add(loadedChat)
                                }
                                processedChildren++
                                if (processedChildren == totalChildren.toInt()) {
                                    chatAdapter.updateChats(newChats.sortedByDescending { c -> c.lastMessageTime })
                                }
                            }
                        } ?: run { // Jika chat null
                            processedChildren++
                            if (processedChildren == totalChildren.toInt()) {
                                chatAdapter.updateChats(newChats.sortedByDescending { c -> c.lastMessageTime })
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ChatsFragment", "Error parsing chat data: ${data.key}", e)
                        processedChildren++
                        if (processedChildren == totalChildren.toInt()) {
                            chatAdapter.updateChats(newChats.sortedByDescending { c -> c.lastMessageTime })
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatsFragment", "Firebase chats listener cancelled.", error.toException())
            }
        }

        val query = chatsReference
            .orderByChild("participants/$currentUserId")
            .equalTo(true)
        query.addValueEventListener(chatsValueEventListener!!)
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (::chatAdapter.isInitialized) {
                    chatAdapter.filter.filter(newText)
                }
                return true
            }
        })
    }

    private fun setupFloatingButtonClickListener() {
        binding.fabNewChat.setOnClickListener {
            showNewChatDialog()
        }
    }

    private fun showNewChatDialog() {
        // TODO: Implement dialog
        Log.d("ChatsFragment", "FAB New Chat clicked")
    }

    override fun onDetach() {
        super.onDetach()
        activityCallback = null // Hapus referensi ke activity
    }

    override fun onDestroyView() {
        super.onDestroyView()
        chatsValueEventListener?.let {
            // Pastikan referensi valid sebelum menghapus listener
            if (currentUserId != null) { // Hanya hapus jika listener pernah ditambahkan
                FirebaseDatabase.getInstance().getReference("chats")
                    .orderByChild("participants/$currentUserId")
                    .equalTo(true)
                    .removeEventListener(it)
            }
        }
        chatsValueEventListener = null
        activityCallback?.clearBottomNavHeightListener(this) // Hapus diri dari listener Activity
        _binding = null
    }
}