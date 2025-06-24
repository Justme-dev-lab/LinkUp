package com.example.linkup // Sesuaikan package

import android.app.Application
import android.app.usage.StorageStatsManager
import android.content.Context
import android.app.usage.StorageStats
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.linkup.model.Chat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.DecimalFormat
import java.util.UUID

// Data class StorageInfo tetap sama
data class StorageInfo(
    val linkupAppUsedStorageFormatted: String,
    val otherAppsUsedStorageFormatted: String,
    val freeDeviceStorageFormatted: String,
    val totalDeviceStorageFormatted: String,
    val linkupAppStoragePercentage: Int,
    val otherAppsStoragePercentage: Int,
    val freeStoragePercentage: Int
)

// Data class ChatStorageDetail tetap sama
data class ChatStorageDetail(
    val chatId: String,
    val chatName: String,
    val recipientProfileImageUrl: String?,
    val storageUsedFormatted: String,
    val storageUsedBytes: Long,
    val lastMessageTime: Long // Digunakan dari model Chat Anda
)

class StorageViewModel(application: Application) : AndroidViewModel(application) {

    private val _storageInfo = MutableLiveData<StorageInfo>()
    val storageInfo: LiveData<StorageInfo> = _storageInfo

    private val _chatStorageList = MutableLiveData<List<ChatStorageDetail>>()
    val chatStorageList: LiveData<List<ChatStorageDetail>> = _chatStorageList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    // PASTIKAN URL DATABASE ANDA SUDAH BENAR DI SINI
    private val database = FirebaseDatabase.getInstance()
    private val firebaseStorage = FirebaseStorage.getInstance()

    companion object {
        private const val TAG = "StorageViewModel"
    }

    fun loadStorageDetails() {
        viewModelScope.launch {
            _isLoading.postValue(true)
            try {
                // Device Storage
                val internalStatFs = StatFs(Environment.getDataDirectory().path)
                val totalBytes = internalStatFs.blockCountLong * internalStatFs.blockSizeLong
                val freeBytes = internalStatFs.availableBlocksLong * internalStatFs.blockSizeLong

                // LinkUp App Storage
                val linkupAppUsedBytes = getLinkUpAppSize(getApplication())

                var otherAppsUsedBytes = totalBytes - freeBytes - linkupAppUsedBytes
                if (otherAppsUsedBytes < 0) otherAppsUsedBytes = 0

                val linkupPercentage = if (totalBytes > 0) ((linkupAppUsedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt() else 0
                val otherAppsPercentage = if (totalBytes > 0) ((otherAppsUsedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt() else 0
                val freePercentage = (100 - linkupPercentage - otherAppsPercentage).coerceAtLeast(0)


                _storageInfo.postValue(
                    StorageInfo(
                        linkupAppUsedStorageFormatted = formatFileSize(linkupAppUsedBytes),
                        otherAppsUsedStorageFormatted = formatFileSize(otherAppsUsedBytes),
                        freeDeviceStorageFormatted = formatFileSize(freeBytes),
                        totalDeviceStorageFormatted = formatFileSize(totalBytes),
                        linkupAppStoragePercentage = linkupPercentage,
                        otherAppsStoragePercentage = otherAppsPercentage,
                        freeStoragePercentage = freePercentage
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading storage details", e)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O) // Pastikan anotasi ini ada untuk fungsi yang menggunakan StorageStatsManager
    private suspend fun getAppSizeWithStorageStatsManager(context: Context, appSpecificInternalDirUuid: UUID): Long {
        val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val stats: StorageStats = storageStatsManager.queryStatsForPackage(appSpecificInternalDirUuid, context.packageName, android.os.Process.myUserHandle())
        // Sekarang referensi ini seharusnya benar karena 'stats' adalah instance dari StorageStats
        return stats.appBytes + stats.dataBytes + stats.cacheBytes
    }

    private suspend fun getLinkUpAppSize(context: Context): Long {
        return withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                try {
                    val appSpecificInternalDirUuid: UUID? = storageManager.getUuidForPath(context.filesDir)
                    if (appSpecificInternalDirUuid != null) {
                        try {
                            getAppSizeWithStorageStatsManager(context, appSpecificInternalDirUuid) // Panggil fungsi yang sudah dianotasi
                        } catch (e: Exception) {
                            Log.e(TAG, "Error getting app size with StorageStatsManager. Falling back.", e)
                            getLocalAppDirectorySize(context)
                        }
                    } else {
                        Log.w(TAG, "UUID for path is null, falling back for app size calculation.")
                        getLocalAppDirectorySize(context)
                    }
                } catch (e: IOException) {
                    Log.w(TAG, "Could not get UUID for path, falling back for app size calculation.", e)
                    getLocalAppDirectorySize(context)
                }
            } else {
                getLocalAppDirectorySize(context)
            }
        }
    }


    private fun getLocalAppDirectorySize(context: Context): Long {
        var size: Long = 0
        val filesDir = context.filesDir
        val cacheDir = context.cacheDir
        val externalCacheDir = context.externalCacheDir
        val externalFilesDirs = context.getExternalFilesDirs(null)

        size += getDirectorySizeRecursively(filesDir)
        size += getDirectorySizeRecursively(cacheDir)
        externalCacheDir?.let { size += getDirectorySizeRecursively(it) }
        externalFilesDirs.forEach { dir ->
            dir?.let { size += getDirectorySizeRecursively(it) }
        }
        Log.d(TAG, "Calculated local app directory size: ${formatFileSize(size)}")
        return size
    }

    private fun getDirectorySizeRecursively(directory: File?): Long {
        if (directory == null || !directory.exists() || !directory.isDirectory) return 0L
        var length: Long = 0
        try {
            directory.listFiles()?.forEach { file ->
                length += if (file.isFile) file.length() else getDirectorySizeRecursively(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not list or access files in ${directory.absolutePath}", e)
        }
        return length
    }


    fun loadChatStorageDetails() {
        if (currentUserId == null) {
            _chatStorageList.postValue(emptyList())
            Log.w(TAG, "Current user ID is null. Cannot load chat storage details.")
            return
        }
        _isLoading.postValue(true)
        Log.d(TAG, "Loading chat storage details for user: $currentUserId")

        val chatsRef = database.getReference("chats")
        chatsRef.orderByChild("participants/$currentUserId").equalTo(true)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        Log.d(TAG, "No chats found for user $currentUserId")
                        _chatStorageList.postValue(emptyList())
                        _isLoading.postValue(false)
                        return
                    }
                    Log.d(TAG, "Found ${snapshot.childrenCount} chats for user $currentUserId")

                    viewModelScope.launch {
                        val chatDetails = mutableListOf<ChatStorageDetail>()
                        snapshot.children.forEach { chatSnapshot ->
                            val chat = chatSnapshot.getValue(Chat::class.java)
                            if (chat != null && chat.id != null && chat.id!!.isNotEmpty()) {
                                Log.d(TAG, "Processing chat: ${chat.id}")

                                var chatName: String
                                var recipientProfileImageUrl: String?

                                if (chat.isGroupChat) {
                                    chatName = chat.groupName ?: "Group Chat"
                                    recipientProfileImageUrl = chat.groupImage
                                } else {
                                    val recipientId = chat.participants.keys.firstOrNull { it != currentUserId }
                                    if (recipientId != null) {
                                        val (name, profileUrl) = getUserDetails(recipientId)
                                        chatName = name
                                        recipientProfileImageUrl = profileUrl
                                    } else {
                                        chatName = "Chat"
                                        recipientProfileImageUrl = null
                                        Log.w(TAG, "Could not find recipient ID for 1-on-1 chat: ${chat.id}")
                                    }
                                }

                                var totalChatSizeBytes: Long = 0
                                val chatIdForPath = chat.id!!

                                val storagePath = "chatMedia/$chatIdForPath"
                                Log.d(TAG, "Checking Firebase Storage path: $storagePath for chat ${chat.id}")
                                try {
                                    val listResult = firebaseStorage.reference.child(storagePath).listAll().await()
                                    Log.d(TAG, "Found ${listResult.items.size} items in Firebase Storage for chat ${chat.id}")
                                    listResult.items.forEach { item ->
                                        try {
                                            val metadata = item.metadata.await()
                                            totalChatSizeBytes += metadata.sizeBytes
                                            Log.d(TAG, "File ${item.name}: ${metadata.sizeBytes} bytes")
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Failed to get metadata for ${item.path}", e)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error listing files for chat $chatIdForPath in Firebase Storage: ${e.message}", e)
                                }

                                val localChatMediaDir = File(getApplication<Application>().filesDir, "media/$chatIdForPath")
                                if (localChatMediaDir.exists() && localChatMediaDir.isDirectory) {
                                    val localSize = getDirectorySizeRecursively(localChatMediaDir)
                                    totalChatSizeBytes += localSize
                                    Log.d(TAG, "Local media size for chat $chatIdForPath: $localSize bytes")
                                }

                                Log.d(TAG, "Total calculated size for chat ${chat.id}: $totalChatSizeBytes bytes")
                                if (totalChatSizeBytes > 0) {
                                    chatDetails.add(
                                        ChatStorageDetail(
                                            chatId = chatIdForPath,
                                            chatName = chatName,
                                            recipientProfileImageUrl = recipientProfileImageUrl,
                                            storageUsedFormatted = formatFileSize(totalChatSizeBytes),
                                            storageUsedBytes = totalChatSizeBytes,
                                            lastMessageTime = chat.lastMessageTime
                                        )
                                    )
                                }
                            } else {
                                Log.w(TAG, "Skipping chat due to null or empty ID, or null chat object. Snapshot: ${chatSnapshot.key}")
                            }
                        }
                        _chatStorageList.postValue(chatDetails.sortedByDescending { it.storageUsedBytes })
                        Log.d(TAG, "Finished processing all chats. Total details added: ${chatDetails.size}")
                        _isLoading.postValue(false)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to load chats: ${error.message}", error.toException())
                    _chatStorageList.postValue(emptyList())
                    _isLoading.postValue(false)
                }
            })
    }

    private suspend fun getUserDetails(userId: String): Pair<String, String?> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching user details for ID: $userId")
                val snapshot = database.getReference("users").child(userId).get().await()
                val username = snapshot.child("username").getValue(String::class.java) ?: "Unknown User"
                val profileUrl = snapshot.child("profile").getValue(String::class.java)
                Log.d(TAG, "Fetched user: $username, Profile URL: $profileUrl")
                Pair(username, profileUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching user details for $userId", e)
                Pair("Unknown User", null)
            }
        }
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        val safeDigitGroups = digitGroups.coerceIn(0, units.size - 1)
        return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, safeDigitGroups.toDouble())) + " " + units[safeDigitGroups]
    }
}