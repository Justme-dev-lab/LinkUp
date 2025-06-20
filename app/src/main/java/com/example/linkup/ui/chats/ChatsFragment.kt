package com.example.linkup.ui.chats

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.linkup.databinding.FragmentChatsBinding
import com.example.linkup.model.Chat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout

class ChatsFragment : Fragment() {

    private var _binding: FragmentChatsBinding? = null
    private val binding get() = _binding!!
    private lateinit var chatsViewModel: ChatsViewModel
    private lateinit var chatAdapter: ChatAdapter
    private val chatList = mutableListOf<Chat>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        chatsViewModel = ViewModelProvider(this).get(ChatsViewModel::class.java)
        _binding = FragmentChatsBinding.inflate(inflater, container, false)

        loadChats()
        setupSearch()
        setupFloatingButton()

        return binding.root
    }

    private fun loadChats() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseDatabase.getInstance().getReference("chats")
            .orderByChild("participants/$currentUserId")
            .equalTo(true)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    chatList.clear()
                    for (data in snapshot.children) {
                        val chat = data.getValue(Chat::class.java)
                        chat?.id = data.key
                        chat?.let { chatList.add(it) }
                    }
                    chatAdapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error
                }
            })
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                chatAdapter.filter.filter(newText)
                return true
            }
        })
    }

    private fun setupFloatingButton() {
        val fab = FloatingActionButton(requireContext()).apply {
            setImageResource(android.R.drawable.ic_input_add)
            setOnClickListener {
                // Show dialog to start new chat
                showNewChatDialog()
            }
        }

        (binding.root as? ConstraintLayout)?.addView(fab)
        fab.layoutParams = (fab.layoutParams as ConstraintLayout.LayoutParams).apply {
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            marginEnd = 32
            bottomMargin = 100 // Above the bottom nav
        }
    }

    private fun showNewChatDialog() {
        // Implement dialog to select user to chat with
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}