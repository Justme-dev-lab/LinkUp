package com.example.linkup.ui.archive // atau package yang sesuai

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linkup.model.Chat // Model Chat utama Anda
import com.example.linkup.model.SelectableChatItem
import com.example.linkup.model.Users // Model User Anda
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SelectChatsArchiveViewModel : ViewModel() {

    private val _chatList = MutableLiveData<List<SelectableChatItem>>()
    val chatList: LiveData<List<SelectableChatItem>> = _chatList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private val database = FirebaseDatabase.getInstance("https://linkup-3b210-default-rtdb.asia-southeast1.firebasedatabase.app")
    private val userChatsRef = database.getReference("user-chats")
    private val chatsRef = database.getReference("chats")
    private val usersRef = database.getReference("users")

    private val TAG = "SelectChatsArchiveVM"

    init {
        loadUserChats()
    }

    fun loadUserChats() {
        if (currentUserId == null) {
            _toastMessage.value = "User not authenticated."
            Log.e(TAG, "Current user ID is null.")
            return
        }

        _isLoading.value = true
        Log.d(TAG, "Loading user chats for $currentUserId")

        userChatsRef.child(currentUserId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Log.d(TAG, "No chats found for user $currentUserId under user-chats.")
                    _chatList.postValue(emptyList())
                    _isLoading.postValue(false)
                    return
                }

                val chatIds = snapshot.children.mapNotNull { it.key }
                Log.d(TAG, "Found ${chatIds.size} chat IDs: $chatIds")
                if (chatIds.isEmpty()) {
                    _chatList.postValue(emptyList())
                    _isLoading.postValue(false)
                    return
                }
                fetchChatDetails(chatIds)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to load user-chats: ${error.message}", error.toException())
                _toastMessage.postValue("Failed to load chats: ${error.message}")
                _isLoading.postValue(false)
            }
        })
    }

    private fun fetchChatDetails(chatIds: List<String>) {
        viewModelScope.launch {
            val selectableChats = mutableListOf<SelectableChatItem>()
            var processedCount = 0

            if (chatIds.isEmpty()) {
                _chatList.postValue(emptyList())
                _isLoading.postValue(false)
                Log.d(TAG, "Chat ID list is empty, posting empty list.")
                return@launch
            }

            Log.d(TAG, "Fetching details for ${chatIds.size} chats.")
            for (chatId in chatIds) {
                try {
                    val chatSnapshot = chatsRef.child(chatId).get().await()
                    val chat = chatSnapshot.getValue(Chat::class.java)

                    if (chat != null && !chat.isArchived) { // Hanya tampilkan yang BELUM diarsip
                        val recipientId = chat.participants.keys.firstOrNull { it != currentUserId }
                        var chatName = chat.lastMessageSenderId // default atau placeholder
                        var profileImageUrl: String? = null

                        if (chat.isGroupChat) {
                            chatName = chat.groupName ?: "Group Chat"
                            profileImageUrl = chat.groupImage
                        } else if (recipientId != null) {
                            val userSnapshot = usersRef.child(recipientId).get().await()
                            val recipientUser = userSnapshot.getValue(Users::class.java)
                            chatName = recipientUser?.username ?: "Unknown User"
                            profileImageUrl = recipientUser?.profile
                        }

                        selectableChats.add(
                            SelectableChatItem(
                                chatId = chatId,
                                chatName = chatName,
                                profileImageUrl = profileImageUrl,
                                lastMessage = chat.lastMessage,
                                isCurrentlyArchived = chat.isArchived // Akan selalu false di sini karena filter di atas
                            )
                        )
                        Log.d(TAG, "Added chat to selectable list: $chatName (ID: $chatId)")
                    } else {
                        Log.d(TAG, "Chat $chatId is null or already archived. Skipping.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching details for chat ID $chatId: ${e.message}", e)
                } finally {
                    processedCount++
                    if (processedCount == chatIds.size) {
                        _chatList.postValue(selectableChats.sortedByDescending { it.lastMessage }) // Atau urutan lain
                        _isLoading.postValue(false)
                        Log.d(TAG, "Finished fetching all chat details. List size: ${selectableChats.size}")
                    }
                }
            }
            if (chatIds.isNotEmpty() && processedCount == 0 && selectableChats.isEmpty()){
                // Kasus dimana semua chat yang ada ternyata sudah diarsip atau error semua
                _chatList.postValue(emptyList())
                _isLoading.postValue(false)
                Log.d(TAG, "All chats were either null, archived, or errored. Posting empty list.")
            }
        }
    }


    fun archiveSelectedChats(selectedChats: List<SelectableChatItem>) {
        if (currentUserId == null) {
            _toastMessage.value = "User not authenticated."
            return
        }
        if (selectedChats.isEmpty()) {
            _toastMessage.value = "No chats selected to archive."
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            var successCount = 0
            selectedChats.forEach { selectableChat ->
                try {
                    // Update di node 'chats'
                    chatsRef.child(selectableChat.chatId).child("isArchived").setValue(true).await()

                    // Update di node 'user-chats' untuk setiap member (opsional tapi baik untuk konsistensi query)
                    // Jika Anda menggunakan user-chats/{userId}/{chatId}/isArchived
                    // val chatSnapshot = chatsRef.child(selectableChat.chatId).get().await()
                    // val chat = chatSnapshot.getValue(Chat::class.java)
                    // chat?.members?.keys?.forEach { memberId ->
                    //    userChatsRef.child(memberId).child(selectableChat.chatId).child("isArchived").setValue(true).await()
                    // }
                    // Namun, jika user-chats hanya untuk daftar ID, maka update di 'chats' sudah cukup
                    // untuk filtering di ChatsFragment nanti.

                    successCount++
                    Log.d(TAG, "Archived chat: ${selectableChat.chatName} (ID: ${selectableChat.chatId})")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to archive chat ${selectableChat.chatId}: ${e.message}", e)
                }
            }
            _isLoading.postValue(false)
            if (successCount == selectedChats.size) {
                _toastMessage.postValue("$successCount chat(s) archived successfully.")
            } else {
                _toastMessage.postValue("Archived $successCount of ${selectedChats.size} chats. Some might have failed.")
            }
            // Muat ulang daftar untuk merefleksikan perubahan (chat yang diarsip akan hilang dari daftar ini)
            loadUserChats()
        }
    }

    fun onToastMessageShown() {
        _toastMessage.value = null
    }
}