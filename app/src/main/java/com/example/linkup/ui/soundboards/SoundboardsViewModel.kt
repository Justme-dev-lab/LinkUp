// SoundboardsViewModel.kt
package com.example.linkup.ui.soundboards

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.example.linkup.R
import com.example.linkup.model.SoundItem
import com.example.linkup.util.Event
import java.util.UUID

class SoundboardsViewModel(application: Application) : AndroidViewModel(application) {

    // Inisialisasi dengan list kosong untuk menghindari null
    private val _soundItems = MutableLiveData<List<SoundItem>>(emptyList())
    val soundItems: LiveData<List<SoundItem>> = _soundItems

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private val _closeDialogEvent = MutableLiveData<Event<Unit>>()
    val closeDialogEvent: LiveData<Event<Unit>> = _closeDialogEvent

    private var databaseReference: DatabaseReference? = null
    private var valueEventListener: ValueEventListener? = null
    private val currentUser = FirebaseAuth.getInstance().currentUser

    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingPosition: Int = -1

    private var storageReference: StorageReference? = null

    init {
        if (currentUser != null) {
            databaseReference = FirebaseDatabase.getInstance()
                .getReference("soundboards")
                .child(currentUser.uid)
            storageReference = FirebaseStorage.getInstance().getReference("user_audio_files/${currentUser.uid}")
            loadSoundItems()
        } else {
            _toastMessage.value = getApplication<Application>().getString(R.string.user_not_logged_in)
            _soundItems.value = emptyList() // Pastikan list kosong jika tidak login
        }
    }

    fun addSoundItem(soundItem: SoundItem) {
        if (currentUser != null && databaseReference != null) {
            val newItemRef = databaseReference!!.push()
            soundItem.id = newItemRef.key ?: ""

            if (soundItem.id.isEmpty()) {
                _toastMessage.value = getApplication<Application>().getString(R.string.failed_generate_id)
                Log.e("SoundVM", "Failed to generate key for new sound item.")
                return
            }

            newItemRef.setValue(soundItem)
                .addOnSuccessListener {
                    _toastMessage.value = "'${soundItem.title}' added successfully."
                    Log.d("SoundVM", "Sound item added: ${soundItem.id}")
                    _closeDialogEvent.value = Event(Unit)
                }
                .addOnFailureListener { e ->
                    _toastMessage.value = "Failed to add sound: ${e.message}"
                    Log.e("SoundVM", "Failed to add sound item", e)
                }
        } else {
            _toastMessage.value = getApplication<Application>().getString(R.string.cannot_add_sound_user_null)
            Log.w("SoundVM", "Add sound attempt failed: User null or DB ref null.")
        }
    }

    fun uploadAudioAndAddSoundItem(title: String, audioUri: Uri) {
        if (currentUser == null || storageReference == null) {
            _toastMessage.value = "Cannot upload: User not logged in or storage not initialized."
            return
        }

        _toastMessage.value = "Uploading audio..."

        // Menggunakan getFileExtension yang sudah diperbaiki
        val fileExtension = getFileExtension(audioUri) ?: "dat"
        val uniqueFileName = "${UUID.randomUUID()}.$fileExtension"
        val fileRef = storageReference!!.child(uniqueFileName)

        fileRef.putFile(audioUri)
            .addOnSuccessListener { taskSnapshot ->
                fileRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    val soundUrl = downloadUri.toString()
                    val newSoundItem = SoundItem(
                        title = title,
                        soundUrl = soundUrl,
                        //iconName = iconName,
                        //backgroundColor = backgroundColor
                    )
                    addSoundItem(newSoundItem)
                }.addOnFailureListener { e ->
                    _toastMessage.value = "Upload success, but failed to get download URL: ${e.message}"
                    Log.e("SoundVM", "Failed to get download URL", e)
                }
            }
            .addOnFailureListener { e ->
                _toastMessage.value = "Audio upload failed: ${e.message}"
                Log.e("SoundVM", "Audio upload failed", e)
            }
            .addOnProgressListener { snapshot ->
                val progress = (100.0 * snapshot.bytesTransferred) / snapshot.totalByteCount
                Log.d("SoundVM", "Upload is $progress% done")
            }
    }

    private fun getFileExtension(uri: Uri): String? {
        val contentResolver = getApplication<Application>().contentResolver
        val mimeTypeMap = android.webkit.MimeTypeMap.getSingleton()
        var extension = mimeTypeMap.getExtensionFromMimeType(contentResolver.getType(uri))

        if (extension == null) {
            extension = uri.lastPathSegment // `lastPathSegment` bisa null
                ?.substringAfterLast('.', missingDelimiterValue = "") // Jika '.' tidak ada, kembalikan ""
                ?.takeIf { it.isNotEmpty() } // Jika hasil substringAfterLast adalah "", jadikan null
        }
        return extension
    }


    private fun loadSoundItems() {
        valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val items = mutableListOf<SoundItem>()
                snapshot.children.forEach { dataSnapshot ->
                    val item = dataSnapshot.getValue(SoundItem::class.java)
                    item?.let {
                        it.id = dataSnapshot.key ?: ""
                        // Karena _soundItems.value sekarang diinisialisasi, kita tidak perlu khawatir itu null di sini
                        // Tapi existingItem tetap bisa null jika item baru
                        val existingItem = _soundItems.value?.find { oldItem -> oldItem.id == it.id }
                        it.isPlayingUi = existingItem?.isPlayingUi ?: false
                        items.add(it)
                    }
                }
                _soundItems.value = items
                Log.d("SoundVM", "Sound items loaded: ${items.size}")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SoundVM", "Failed to load sound items.", error.toException())
                _toastMessage.value = "Failed to load sounds: ${error.message}"
                _soundItems.value = emptyList() // Set ke list kosong jika gagal load
            }
        }
        databaseReference?.addValueEventListener(valueEventListener!!)
    }

    fun playSound(soundItem: SoundItem, position: Int) {
        // Karena _soundItems.value diinisialisasi dengan emptyList(), kita bisa berasumsi itu tidak null.
        // Namun, currentList tetap bisa menjadi list kosong.
        val currentList = _soundItems.value?.toMutableList() ?: mutableListOf() // Fallback ke mutableList kosong jika _soundItems.value null (seharusnya tidak terjadi)


        if (currentlyPlayingPosition != -1 && currentlyPlayingPosition != position) {
            // Aman karena currentList sekarang dijamin non-null (meskipun bisa kosong)
            currentList.getOrNull(currentlyPlayingPosition)?.isPlayingUi = false
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }

        val itemInList = currentList.getOrNull(position)
        // Jika item yang sama diklik lagi saat sedang berputar
        if (itemInList?.id == soundItem.id && itemInList.isPlayingUi) {
            itemInList.isPlayingUi = false
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            currentlyPlayingPosition = -1
            _soundItems.value = currentList
            return
        }

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(soundItem.soundUrl)
                setOnPreparedListener { mp ->
                    mp.start()
                    // Update item di list
                    // Kita perlu memastikan bahwa kita memodifikasi list yang akan di-set ke LiveData
                    val freshListOnPrepared = _soundItems.value?.toMutableList() ?: mutableListOf()
                    freshListOnPrepared.find { it.id == soundItem.id }?.isPlayingUi = true
                    _soundItems.value = freshListOnPrepared

                    currentlyPlayingPosition = position // position dari parameter fungsi
                    Log.d("SoundVM", "Playing: ${soundItem.title}")
                }
                setOnCompletionListener { mp ->
                    // soundItem.isPlayingUi = false // Sebaiknya update list langsung
                    val freshListCompletion = _soundItems.value?.toMutableList() ?: mutableListOf()
                    freshListCompletion.find { it.id == soundItem.id }?.isPlayingUi = false
                    _soundItems.value = freshListCompletion

                    mp.release()
                    mediaPlayer = null
                    currentlyPlayingPosition = -1
                    Log.d("SoundVM", "Completed: ${soundItem.title}")
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e("SoundVM", "MediaPlayer Error: what $what, extra $extra for ${soundItem.soundUrl}")
                    _toastMessage.value = "Error playing sound: ${soundItem.title}"

                    val freshListError = _soundItems.value?.toMutableList() ?: mutableListOf()
                    freshListError.find { it.id == soundItem.id }?.isPlayingUi = false
                    _soundItems.value = freshListError

                    mp.release()
                    mediaPlayer = null
                    currentlyPlayingPosition = -1
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("SoundVM", "Error setting up MediaPlayer for ${soundItem.soundUrl}", e)
            _toastMessage.value = "Cannot play sound: ${soundItem.title}"
            // Update UI jika gagal
            val freshListCatch = _soundItems.value?.toMutableList() ?: mutableListOf()
            freshListCatch.find { it.id == soundItem.id }?.isPlayingUi = false
            _soundItems.value = freshListCatch
        }
    }


    fun deleteSound(soundItem: SoundItem) {
        if (currentUser != null && databaseReference != null) {
            databaseReference!!.child(soundItem.id).removeValue()
                .addOnSuccessListener {
                    _toastMessage.value = "'${soundItem.title}' deleted."
                    Log.d("SoundVM", "Sound metadata deleted: ${soundItem.id}")

                    if (soundItem.soundUrl.startsWith("https://firebasestorage.googleapis.com/")) {
                        try {
                            val storageRefToDelete = FirebaseStorage.getInstance().getReferenceFromUrl(soundItem.soundUrl)
                            storageRefToDelete.delete()
                                .addOnSuccessListener {
                                    Log.d("SoundVM", "Associated audio file deleted from Storage: ${soundItem.soundUrl}")
                                }
                                .addOnFailureListener { e_storage ->
                                    Log.e("SoundVM", "Failed to delete audio file from Storage: ${soundItem.soundUrl}", e_storage)
                                    _toastMessage.value = "List item deleted, but failed to delete audio file: ${e_storage.message}"
                                }
                        } catch (e: Exception) {
                            Log.e("SoundVM", "Error creating storage reference from URL for deletion: ${soundItem.soundUrl}", e)
                            _toastMessage.value = "List item deleted, but error with audio file URL for deletion."
                        }
                    }

                    // Periksa apakah item yang dihapus adalah yang sedang diputar
                    // Gunakan ID untuk perbandingan yang lebih aman
                    if (soundItem.id == _soundItems.value?.getOrNull(currentlyPlayingPosition)?.id) {
                        _soundItems.value?.getOrNull(currentlyPlayingPosition)?.isPlayingUi = false // Set isPlayingUi dari item di list jika ada
                        mediaPlayer?.stop()
                        mediaPlayer?.release()
                        mediaPlayer = null
                        currentlyPlayingPosition = -1
                        // _soundItems.value akan diupdate oleh listener onDataChange dari Firebase
                    }
                }
                .addOnFailureListener { e_rtdb ->
                    _toastMessage.value = "Failed to delete '${soundItem.title}': ${e_rtdb.message}"
                    Log.e("SoundVM", "Failed to delete sound metadata: ${soundItem.id}", e_rtdb)
                }
        }
    }

    fun stopAnyPlayingSound() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        if (currentlyPlayingPosition != -1) {
            // _soundItems.value seharusnya tidak null di sini karena inisialisasi
            val currentList = _soundItems.value?.toMutableList()
            currentList?.getOrNull(currentlyPlayingPosition)?.isPlayingUi = false
            _soundItems.value = currentList ?: emptyList() // Set ke list (mungkin diubah) atau list kosong
            currentlyPlayingPosition = -1
        }
    }

    override fun onCleared() {
        super.onCleared()
        valueEventListener?.let { listener ->
            databaseReference?.removeEventListener(listener)
        }
        stopAnyPlayingSound()
        Log.d("SoundVM", "ViewModel cleared, MediaPlayer released.")
    }

    fun consumeToastMessage() {
        _toastMessage.value = null
    }
}