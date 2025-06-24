package com.example.linkup.ui.chats

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.linkup.AddFriendActivity
import com.example.linkup.MessageChatActivity
import com.example.linkup.ProfileActivity
import com.example.linkup.R
import com.example.linkup.databinding.FragmentChatsBinding
import com.example.linkup.model.Chat
import com.example.linkup.ui.BottomNavHeightListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.Query // Pastikan ini diimpor

class ChatsFragment : Fragment(), BottomNavHeightListener {

    private var _binding: FragmentChatsBinding? = null
    private val binding get() = _binding!!

    private var currentBottomNavHeight = 0
    private var currentImeHeight = 0

    private lateinit var chatAdapter: ChatAdapter
    // Hapus chatsReference dari sini jika tidak digunakan di luar attachChatsListener
    // private lateinit var chatsReference: DatabaseReference
    private var chatsValueEventListener: ValueEventListener? = null
    private var currentUserId: String? = null
    private var firebaseUser: FirebaseUser? = null
    private var currentQuery: Query? = null // Variabel untuk menyimpan query saat ini

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
        firebaseUser = FirebaseAuth.getInstance().currentUser
        currentUserId = firebaseUser?.uid
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViewAdapter()
        setupRecyclerViewLayout()
        setupSearch()
        setupFloatingButtonClickListener()
        applyStatusBarPaddingToHeader()

        activityCallback?.requestBottomNavHeight(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemGestureInsets = insets.getInsets(WindowInsetsCompat.Type.systemGestures())
            currentImeHeight = imeInsets.bottom
            val effectiveBottomNavHeight = currentBottomNavHeight + systemGestureInsets.bottom
            adjustViewsForBottomInsets(effectiveBottomNavHeight, currentImeHeight)
            insets
        }

        if (currentUserId != null) {
            attachChatsListener()
            loadCurrentUserProfileImageToHeader()
        } else {
            Log.w("ChatsFragment", "User not logged in, cannot load chats or profile image.")
            if (_binding != null) {
                binding.profileButton.setImageResource(R.drawable.ic_profile)
            }
        }

        binding.profileButton.setOnClickListener {
            val intent = Intent(requireContext(), ProfileActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadCurrentUserProfileImageToHeader() {
        val userId = currentUserId ?: return
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && isAdded && _binding != null) {
                    val profileImageUrl = snapshot.child("profile").getValue(String::class.java)
                    if (!profileImageUrl.isNullOrEmpty()) {
                        Glide.with(requireContext())
                            .load(profileImageUrl)
                            .placeholder(R.drawable.ic_profile)
                            .apply(RequestOptions.circleCropTransform())
                            .into(binding.profileButton)
                    } else {
                        binding.profileButton.setImageResource(R.drawable.ic_profile)
                        Log.w("ChatsFragment", "Profile image URL is null or empty for header button.")
                    }
                } else if (isAdded && _binding != null) {
                    binding.profileButton.setImageResource(R.drawable.ic_profile)
                    if (!snapshot.exists()){
                        Log.w("ChatsFragment", "User data not found for profile image in header for UID: $userId")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatsFragment", "Failed to load profile image for header.", error.toException())
                if (isAdded && _binding != null) {
                    binding.profileButton.setImageResource(R.drawable.ic_profile)
                }
            }
        })
    }

    override fun onBottomNavHeightCalculated(height: Int) {
        if (!isAdded || _binding == null) return
        Log.d("ChatsFragment", "onBottomNavHeightCalculated called with height: $height")
        if (height > 0) {
            currentBottomNavHeight = height
            val systemGestureInsetsBottom = view?.let {
                ViewCompat.getRootWindowInsets(it)?.getInsets(WindowInsetsCompat.Type.systemGestures())?.bottom ?: 0
            } ?: 0
            val effectiveBottomNavHeight = currentBottomNavHeight + systemGestureInsetsBottom
            adjustViewsForBottomInsets(effectiveBottomNavHeight, currentImeHeight)
            Log.d("ChatsFragment", "Adjusting from onBottomNavHeightCalculated. EffectiveBNavH: $effectiveBottomNavHeight, IMEH: $currentImeHeight")
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("ChatsFragment", "onResume called")
        activityCallback?.requestBottomNavHeight(this)
        view?.let {
            Log.d("ChatsFragment", "Requesting apply insets on resume")
            ViewCompat.requestApplyInsets(it)
        }
        if (isAdded && _binding != null && (currentBottomNavHeight > 0 || currentImeHeight >= 0)) {
            Log.d("ChatsFragment", "onResume: Explicitly calling adjustViewsForBottomInsets with BNavH=$currentBottomNavHeight, IMEH=$currentImeHeight")
            val systemGestureInsetsBottom = view?.let {
                ViewCompat.getRootWindowInsets(it)?.getInsets(WindowInsetsCompat.Type.systemGestures())?.bottom ?: 0
            } ?: 0
            val effectiveBottomNavHeight = currentBottomNavHeight + systemGestureInsetsBottom
            adjustViewsForBottomInsets(effectiveBottomNavHeight, currentImeHeight)
        }
        if (currentUserId != null && isAdded && _binding != null) {
            loadCurrentUserProfileImageToHeader()
        }
    }

    private fun adjustViewsForBottomInsets(bottomNavHeightToUse: Int, imeHeightToUse: Int) {
        if (!isAdded || _binding == null) return
        val fabHeightWithMargin = resources.getDimensionPixelSize(R.dimen.fab_size_plus_margin)
        val fabMarginFromBottomNav = resources.getDimensionPixelSize(R.dimen.fab_margin_from_bottom_nav)
        val recyclerViewPaddingBottom = bottomNavHeightToUse + fabHeightWithMargin + imeHeightToUse
        binding.chatRecyclerView.updatePadding(bottom = recyclerViewPaddingBottom)
        binding.fabNewChat.updateLayoutParams<ConstraintLayout.LayoutParams> {
            bottomMargin = bottomNavHeightToUse + fabMarginFromBottomNav + imeHeightToUse
        }
    }

    private fun applyStatusBarPaddingToHeader() {
        if (_binding == null) return
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerTitle) { headerView, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            headerView.updatePadding(top = systemBars.top)
            WindowInsetsCompat.Builder(insets).setInsets(
                WindowInsetsCompat.Type.systemBars(),
                androidx.core.graphics.Insets.of(systemBars.left, 0, systemBars.right, systemBars.bottom)
            ).build()
        }
    }

    private fun setupRecyclerViewAdapter() {
        chatAdapter = ChatAdapter(currentUserId) { chat ->
            val otherParticipantId = chat.participants.keys.firstOrNull { it != currentUserId }
            if (otherParticipantId != null || chat.isGroupChat) {
                val intent = Intent(requireContext(), MessageChatActivity::class.java)
                if (chat.isGroupChat) {
                    intent.putExtra(MessageChatActivity.EXTRA_USER_ID, chat.id)
                    intent.putExtra(MessageChatActivity.EXTRA_USER_NAME, chat.groupName ?: "Group Chat")
                    intent.putExtra(MessageChatActivity.EXTRA_PROFILE_IMAGE_URL, chat.groupImage ?: "")
                } else if (otherParticipantId != null) {
                    intent.putExtra(MessageChatActivity.EXTRA_USER_ID, otherParticipantId)
                    intent.putExtra(MessageChatActivity.EXTRA_USER_NAME, chat.recipientName)
                    intent.putExtra(MessageChatActivity.EXTRA_PROFILE_IMAGE_URL, chat.recipientProfileImage)
                }
                startActivity(intent)
            } else {
                Log.w("ChatsFragment", "Could not determine recipient for chat: ${chat.id}")
                Toast.makeText(requireContext(), "Cannot open chat.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerViewLayout() {
        if (_binding == null) return
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
            setHasFixedSize(true)
        }
    }

    private fun attachChatsListener() {
        if (currentUserId == null) {
            Log.w("ChatsFragment", "CurrentUserId is null in attachChatsListener.")
            return
        }
        if (_binding == null || !::chatAdapter.isInitialized) {
            Log.e("ChatsFragment", "Binding is null or chatAdapter not initialized in attachChatsListener")
            return
        }

        // Dapatkan referensi database sekali saja
        val chatsDbReference = FirebaseDatabase.getInstance().getReference("chats")

        // 1. Hapus listener lama dari query sebelumnya JIKA ADA
        chatsValueEventListener?.let { listener ->
            currentQuery?.removeEventListener(listener)
            Log.d("ChatsFragment", "Previous listener removed from query.")
        }

        // 2. Buat ValueEventListener baru (jika belum ada atau jika logicnya berbeda setiap kali)
        // Jika listener selalu sama, Anda bisa mendefinisikannya sekali saja sebagai properti kelas.
        // Untuk saat ini, kita buat ulang di sini untuk memastikan instance baru.
        chatsValueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || _binding == null || !::chatAdapter.isInitialized) {
                    Log.w("ChatsFragment", "onDataChange: Fragment not added, binding is null, or adapter not initialized.")
                    return
                }

                val newChats = mutableListOf<Chat>()
                val totalChildren = snapshot.childrenCount
                var processedChildren = 0

                Log.d("ChatsFragment", "onDataChange triggered. Total children: $totalChildren")

                if (totalChildren == 0L) {
                    chatAdapter.updateChats(emptyList())
                    Log.d("ChatsFragment", "No chats found, adapter updated with empty list.")
                    return
                }

                for (data in snapshot.children) {
                    try {
                        val chat = data.getValue(Chat::class.java)
                        chat?.id = data.key // Penting untuk DiffUtil dan identifikasi
                        chat?.let { loadedChat ->
                            val otherParticipantId = loadedChat.participants.keys.firstOrNull { key -> key != currentUserId }
                            if (otherParticipantId != null) {
                                FirebaseDatabase.getInstance().getReference("users").child(otherParticipantId)
                                    .addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(userSnapshot: DataSnapshot) {
                                            if (!isAdded) return // Cek lagi sebelum memproses
                                            loadedChat.recipientName = userSnapshot.child("username").getValue(String::class.java) ?: "Unknown User"
                                            loadedChat.recipientProfileImage = userSnapshot.child("profile").getValue(String::class.java) ?: ""
                                            synchronized(newChats) { newChats.add(loadedChat) }
                                            processedChildren++
                                            if (processedChildren == totalChildren.toInt()) {
                                                if (isAdded && _binding != null && ::chatAdapter.isInitialized) {
                                                    chatAdapter.updateChats(newChats.sortedByDescending { c -> c.lastMessageTime })
                                                    Log.d("ChatsFragment", "Adapter updated with ${newChats.size} chats (user data fetched).")
                                                }
                                            }
                                        }
                                        override fun onCancelled(error: DatabaseError) {
                                            if (!isAdded) return
                                            Log.e("ChatsFragment", "Failed to load user data for chat: $otherParticipantId", error.toException())
                                            loadedChat.recipientName = "Unknown User" // Default
                                            synchronized(newChats) { newChats.add(loadedChat) }
                                            processedChildren++
                                            if (processedChildren == totalChildren.toInt()) {
                                                if (isAdded && _binding != null && ::chatAdapter.isInitialized) {
                                                    chatAdapter.updateChats(newChats.sortedByDescending { c -> c.lastMessageTime })
                                                    Log.d("ChatsFragment", "Adapter updated with ${newChats.size} chats (user data fetch failed).")
                                                }
                                            }
                                        }
                                    })
                            } else { // Ini bisa jadi group chat atau chat dengan diri sendiri (jika memungkinkan)
                                loadedChat.recipientName = if (loadedChat.isGroupChat) loadedChat.groupName ?: "Group Chat" else "Self Chat"
                                // Untuk group chat, Anda mungkin ingin mengambil gambar grup dari loadedChat.groupImage
                                synchronized(newChats) { newChats.add(loadedChat) }
                                processedChildren++
                                if (processedChildren == totalChildren.toInt()) {
                                    if (isAdded && _binding != null && ::chatAdapter.isInitialized) {
                                        chatAdapter.updateChats(newChats.sortedByDescending { c -> c.lastMessageTime })
                                        Log.d("ChatsFragment", "Adapter updated with ${newChats.size} chats (group/self chat).")
                                    }
                                }
                            }
                        } ?: run { // Jika chat null setelah deserialisasi
                            processedChildren++
                            Log.w("ChatsFragment", "Chat data was null for key: ${data.key}")
                            if (processedChildren == totalChildren.toInt()) {
                                if (isAdded && _binding != null && ::chatAdapter.isInitialized) {
                                    chatAdapter.updateChats(newChats.sortedByDescending { c -> c.lastMessageTime })
                                    Log.d("ChatsFragment", "Adapter updated with ${newChats.size} chats (null chat data encountered).")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ChatsFragment", "Error parsing chat data: ${data.key}", e)
                        processedChildren++
                        if (processedChildren == totalChildren.toInt()) {
                            if (isAdded && _binding != null && ::chatAdapter.isInitialized) {
                                chatAdapter.updateChats(newChats.sortedByDescending { c -> c.lastMessageTime })
                                Log.d("ChatsFragment", "Adapter updated with ${newChats.size} chats (error parsing chat data).")
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isAdded) return
                Log.e("ChatsFragment", "Firebase chats listener cancelled.", error.toException())
                // Mungkin tampilkan pesan error ke pengguna
            }
        }

        // 3. Buat query baru dan simpan
        currentQuery = chatsDbReference
            .orderByChild("participants/$currentUserId")
            .equalTo(true)

        // 4. Tambahkan listener ke query baru
        currentQuery!!.addValueEventListener(chatsValueEventListener!!)
        Log.d("ChatsFragment", "New listener added to query for userId: $currentUserId")
    }


    private fun setupSearch() {
        if (_binding == null) return
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
        if (_binding == null) return
        binding.fabNewChat.setOnClickListener {
            val intent = Intent(requireContext(), AddFriendActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onDetach() {
        super.onDetach()
        activityCallback = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("ChatsFragment", "onDestroyView called")

        // Hapus listener dari query saat ini
        chatsValueEventListener?.let { listener ->
            currentQuery?.removeEventListener(listener)
            Log.d("ChatsFragment", "Listener removed from query in onDestroyView.")
        }
        // Reset referensi
        chatsValueEventListener = null
        currentQuery = null

        activityCallback?.clearBottomNavHeightListener(this)
        _binding = null // Ini penting!
    }
}