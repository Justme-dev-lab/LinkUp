package com.example.linkup.ui.chats

import android.content.Context
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
import com.example.linkup.R
import com.example.linkup.databinding.FragmentChatsBinding
import com.example.linkup.model.Chat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ChatsFragment : Fragment(), BottomNavHeightListener {

    private var _binding: FragmentChatsBinding? = null
    private val binding get() = _binding!!

    private var currentBottomNavHeight = 0
    private var currentImeHeight = 0

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var chatsReference: DatabaseReference
    private var chatsValueEventListener: ValueEventListener? = null
    private var currentUserId: String? = null

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

        setupRecyclerViewAdapter()
        setupRecyclerViewLayout()
        setupSearch()
        setupFloatingButtonClickListener() // Panggil ini untuk setup listener FAB
        applyStatusBarPaddingToHeader()

        activityCallback?.requestBottomNavHeight(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            Log.d("ChatsFragment", "OnApplyWindowInsetsListener triggered")
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemGestureInsets = insets.getInsets(WindowInsetsCompat.Type.systemGestures())

            currentImeHeight = imeInsets.bottom // Langsung update

            val effectiveBottomNavHeight = currentBottomNavHeight + systemGestureInsets.bottom
            adjustViewsForBottomInsets(effectiveBottomNavHeight, currentImeHeight)
            Log.d("ChatsFragment", "Adjusting from OnApplyWindowInsetsListener. EffectiveBNavH: $effectiveBottomNavHeight, IMEH: $currentImeHeight")
            insets
        }

        if (currentUserId != null) {
            attachChatsListener()
        } else {
            Log.w("ChatsFragment", "User not logged in, cannot load chats.")
        }

        binding.profileButton.setOnClickListener {
            // TODO: Implement navigation to profile screen
        }
    }

    override fun onBottomNavHeightCalculated(height: Int) {
        if (!isAdded || _binding == null) return
        Log.d("ChatsFragment", "onBottomNavHeightCalculated called with height: $height")

        if (height > 0) {
            currentBottomNavHeight = height // Langsung update

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
            Log.d("ChatsFragment", "Chat clicked: ${chat.id}")
        }
    }

    private fun setupRecyclerViewLayout() {
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
            setHasFixedSize(true)
        }
    }

    private fun attachChatsListener() {
        if (currentUserId == null) return
        chatsReference = FirebaseDatabase.getInstance().getReference("chats")
        chatsValueEventListener?.let { chatsReference.removeEventListener(it) }

        chatsValueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newChats = mutableListOf<Chat>()
                val totalChildren = snapshot.childrenCount
                var processedChildren = 0

                if (totalChildren == 0L) {
                    chatAdapter.updateChats(emptyList())
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
                                            synchronized(newChats) { newChats.add(loadedChat) }
                                            processedChildren++
                                            if (processedChildren == totalChildren.toInt()) {
                                                chatAdapter.updateChats(newChats.sortedByDescending { c -> c.lastMessageTime })
                                            }
                                        }
                                        override fun onCancelled(error: DatabaseError) {
                                            Log.e("ChatsFragment", "Failed to load user data for chat: $otherParticipantId", error.toException())
                                            loadedChat.recipientName = "Unknown User"
                                            synchronized(newChats) { newChats.add(loadedChat) }
                                            processedChildren++
                                            if (processedChildren == totalChildren.toInt()) {
                                                chatAdapter.updateChats(newChats.sortedByDescending { c -> c.lastMessageTime })
                                            }
                                        }
                                    })
                            } else {
                                loadedChat.recipientName = if (loadedChat.isGroupChat) loadedChat.groupName ?: "Group Chat" else "Self Chat"
                                synchronized(newChats) { newChats.add(loadedChat) }
                                processedChildren++
                                if (processedChildren == totalChildren.toInt()) {
                                    chatAdapter.updateChats(newChats.sortedByDescending { c -> c.lastMessageTime })
                                }
                            }
                        } ?: run {
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
        val query = chatsReference.orderByChild("participants/$currentUserId").equalTo(true)
        query.addValueEventListener(chatsValueEventListener!!)
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                if (::chatAdapter.isInitialized) chatAdapter.filter.filter(newText)
                return true
            }
        })
    }

    // Dipanggil dari onViewCreated
    private fun setupFloatingButtonClickListener() {
        binding.fabNewChat.setOnClickListener {
            // TODO: Implementasi logika untuk "menambah teman"
            // Misalnya, navigasi ke Fragment/Activity baru untuk mencari dan menambah teman
            Log.d("ChatsFragment", "FAB New Chat (Add Friend) clicked")
            // Contoh navigasi jika Anda menggunakan Navigation Component:
            // findNavController().navigate(R.id.action_chatsFragment_to_findFriendsFragment)
        }
    }

    // Hapus showNewChatDialog() jika tidak digunakan lagi, atau ganti namanya menjadi lebih sesuai
    // private fun showNewChatDialog() {
    //     Log.d("ChatsFragment", "FAB New Chat clicked")
    // }

    override fun onDetach() {
        super.onDetach()
        activityCallback = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        chatsValueEventListener?.let {
            if (currentUserId != null) {
                FirebaseDatabase.getInstance().getReference("chats")
                    .orderByChild("participants/$currentUserId")
                    .equalTo(true)
                    .removeEventListener(it)
            }
        }
        chatsValueEventListener = null
        activityCallback?.clearBottomNavHeightListener(this)
        _binding = null
    }
}