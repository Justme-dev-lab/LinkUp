package com.example.linkup.ui.soundboards

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.linkup.MainActivity // Atau interface ActivityCallback jika sudah dipisah
import com.example.linkup.ProfileActivity
import com.example.linkup.R
import com.example.linkup.databinding.FragmentSoundboardsBinding
import com.example.linkup.model.SoundItem
import com.example.linkup.ui.BottomNavHeightListener
import com.example.linkup.ui.GridSpacingItemDecoration
import com.google.firebase.auth.FirebaseAuth // <-- TAMBAHKAN IMPORT
import com.google.firebase.auth.FirebaseUser // <-- TAMBAHKAN IMPORT
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
// Di SoundboardsFragment.kt
import com.example.linkup.utils.Event // atau path yang benar ke Event.kt
import com.example.linkup.utils.EventObserver // atau path yang benar ke Event.kt

class SoundboardsFragment : Fragment(), BottomNavHeightListener {

    private var _binding: FragmentSoundboardsBinding? = null
    private val binding get() = _binding!!

    private val soundboardsViewModel: SoundboardsViewModel by viewModels()
    private lateinit var soundAdapter: SoundAdapter

    private var activityCallback: MainActivity? = null

    private var selectedAudioUri: Uri? = null
    private lateinit var pickAudioLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private var alertDialog: AlertDialog? = null
    private var selectedFileInfoTextView: TextView? = null

    private var currentBottomNavHeight = 0
    private var currentImeHeight = 0

    // --- TAMBAHKAN VARIABEL INI ---
    private var firebaseUser: FirebaseUser? = null
    private var currentUserId: String? = null
    // -----------------------------

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MainActivity) {
            activityCallback = context
        } else {
            Log.e("SoundboardsFragment", "$context must implement MainActivity (or your ActivityCallback interface)")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- INISIALISASI firebaseUser dan currentUserId ---
        firebaseUser = FirebaseAuth.getInstance().currentUser
        currentUserId = firebaseUser?.uid
        // ----------------------------------------------------

        pickAudioLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    selectedAudioUri = uri
                    selectedFileInfoTextView?.text = "File: ${uri.lastPathSegment ?: "Selected"}"
                    Log.d("SoundFragment", "Audio file selected: $uri")
                }
            } else {
                Log.d("SoundFragment", "Audio file selection cancelled or failed.")
            }
        }

        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                openAudioPicker()
            } else {
                Toast.makeText(requireContext(), "Storage permission is required to select audio files.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSoundboardsBinding.inflate(inflater, container, false)
        // Pindahkan inisialisasi firebaseUser dan currentUserId ke onCreate jika belum
        // firebaseUser = FirebaseAuth.getInstance().currentUser
        // currentUserId = firebaseUser?.uid
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        applyStatusBarPaddingToHeader()

        binding.addSound.setOnClickListener {
            showAddSoundDialog()
        }

        // Menggunakan ID ImageView dari layout header (header_layout_with_profile.xml)
        // Asumsi binding.headerSoundboard adalah include dari layout tersebut.
        // Jika binding.profileButton adalah ImageView-nya langsung, maka lebih sederhana.
        // Mari asumsikan binding.profileButton adalah ImageView yang benar.
        binding.profileButton.setOnClickListener {
            val intent = Intent(requireContext(), ProfileActivity::class.java)
            startActivity(intent)
        }

        // --- PANGGIL FUNGSI UNTUK MEMUAT GAMBAR PROFIL ---
        if (currentUserId != null) {
            loadCurrentUserProfileImageToHeader()
        } else {
            Log.w("SoundboardsFragment", "User not logged in, cannot load profile image.")
            // Set gambar default jika user tidak login
            if (_binding != null) { // Cek null safety untuk binding
                binding.profileButton.setImageResource(R.drawable.ic_profile)
            }
        }
        // -------------------------------------------------

        activityCallback?.requestBottomNavHeight(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemGestureInsets = insets.getInsets(WindowInsetsCompat.Type.systemGestures())
            currentImeHeight = imeInsets.bottom
            val effectiveBottomNavHeight = currentBottomNavHeight + systemGestureInsets.bottom
            adjustViewsForBottomInsets(effectiveBottomNavHeight, currentImeHeight)
            insets
        }
    }

    private fun loadCurrentUserProfileImageToHeader() {
        // Pastikan currentUserId tidak null sebelum melanjutkan
        val userId = currentUserId ?: run {
            Log.w("SoundboardsFragment", "loadCurrentUserProfileImageToHeader: currentUserId is null.")
            if (isAdded && _binding != null) {
                binding.profileButton.setImageResource(R.drawable.ic_profile)
            }
            return
        }

        val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Tambahkan pengecekan `isAdded` dan `_binding != null` untuk menghindari crash
                // jika fragment dihancurkan sebelum callback selesai.
                if (!isAdded || _binding == null) return

                if (snapshot.exists()) {
                    val profileImageUrl = snapshot.child("profile").getValue(String::class.java)

                    if (!profileImageUrl.isNullOrEmpty()) {
                        Glide.with(requireContext()) // Gunakan requireContext() yang lebih aman di Fragment
                            .load(profileImageUrl)
                            .placeholder(R.drawable.ic_profile)
                            .error(R.drawable.ic_profile_error) // Tambahkan drawable error untuk kasus gagal load
                            .apply(RequestOptions.circleCropTransform())
                            .into(binding.profileButton) // Pastikan ID ini benar
                    } else {
                        binding.profileButton.setImageResource(R.drawable.ic_profile)
                        Log.w("SoundboardsFragment", "Profile image URL is null or empty for header button.")
                    }
                } else {
                    binding.profileButton.setImageResource(R.drawable.ic_profile)
                    Log.w("SoundboardsFragment", "User data not found for profile image in header for UID: $userId")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isAdded || _binding == null) return

                Log.e("SoundboardsFragment", "Failed to load profile image for header.", error.toException())
                binding.profileButton.setImageResource(R.drawable.ic_profile_error) // Tampilkan ikon error
            }
        })
    }


    // Fungsi untuk padding status bar (gunakan ID header yang benar)
    private fun applyStatusBarPaddingToHeader() {
        if (_binding == null) return
        // Asumsi binding.headerTitle adalah TextView di dalam layout header Anda
        // Jika header Anda adalah include, Anda mungkin perlu mengaksesnya seperti:
        // val headerView = binding.root.findViewById<View>(R.id.your_header_include_id)
        // ViewCompat.setOnApplyWindowInsetsListener(headerView.findViewById(R.id.actual_title_textview_id_inside_header)) { ... }
        // Untuk sementara, kita asumsikan binding.headerTitle adalah view yang tepat.
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerTitle) { headerView, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            headerView.updatePadding(top = systemBars.top)
            WindowInsetsCompat.Builder(insets).setInsets(
                WindowInsetsCompat.Type.systemBars(),
                androidx.core.graphics.Insets.of(systemBars.left, 0, systemBars.right, systemBars.bottom)
            ).build()
        }
    }

    private fun adjustViewsForBottomInsets(bottomNavHeightToUse: Int, imeHeightToUse: Int) {
        if (!isAdded || _binding == null) return
        val recyclerViewPaddingBottom = bottomNavHeightToUse + imeHeightToUse +
                resources.getDimensionPixelSize(R.dimen.recycler_view_extra_padding_bottom)
        binding.recyclerView.updatePadding(bottom = recyclerViewPaddingBottom)
    }

    private fun showAddSoundDialog() {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val titleInput = EditText(context).apply {
            hint = "Sound Title (Required)"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        layout.addView(titleInput)

        val selectFileButton = Button(context).apply {
            text = "Select Local Audio File"
            setOnClickListener {
                checkStoragePermissionAndOpenPicker()
            }
        }
        layout.addView(selectFileButton)

        selectedFileInfoTextView = TextView(context).apply {
            text = "No file selected"
            setPadding(0, 16, 0, 16)
        }
        layout.addView(selectedFileInfoTextView)

        val soundUrlInput = EditText(context).apply {
            hint = "Or Enter Sound URL (Optional)"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        layout.addView(soundUrlInput)

        alertDialog = AlertDialog.Builder(context)
            .setTitle("Add New Sound")
            .setView(layout)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel") { dialog, _ ->
                selectedAudioUri = null
                selectedFileInfoTextView?.text = "No file selected"
                dialog.cancel()
            }
            .create()

        alertDialog?.show()

        alertDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val title = titleInput.text.toString().trim()
            val externalSoundUrl = soundUrlInput.text.toString().trim()

            if (title.isEmpty()) {
                Toast.makeText(context, "Sound Title cannot be empty.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedAudioUri != null) {
                soundboardsViewModel.uploadAudioAndAddSoundItem(title, selectedAudioUri!!)
            } else if (externalSoundUrl.isNotEmpty()) {
                if (!android.util.Patterns.WEB_URL.matcher(externalSoundUrl).matches()) {
                    Toast.makeText(context, "Invalid Sound URL format.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val newSoundItem = SoundItem(
                    title = title,
                    soundUrl = externalSoundUrl
                )
                soundboardsViewModel.addSoundItem(newSoundItem)
            } else {
                Toast.makeText(context, "Please select an audio file or enter a URL.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
        }
    }

    private fun checkStoragePermissionAndOpenPicker() {
        val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                permissionToRequest
            ) == PackageManager.PERMISSION_GRANTED -> {
                openAudioPicker()
            }
            shouldShowRequestPermissionRationale(permissionToRequest) -> {
                AlertDialog.Builder(requireContext())
                    .setTitle("Storage Permission Needed")
                    .setMessage("This app needs permission to access your audio files to add them to the soundboard.")
                    .setPositiveButton("OK") { _, _ ->
                        requestPermissionLauncher.launch(permissionToRequest)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> {
                requestPermissionLauncher.launch(permissionToRequest)
            }
        }
    }

    private fun openAudioPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            pickAudioLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("SoundFragment", "Error launching audio picker", e)
            Toast.makeText(requireContext(), "Cannot open file picker. Is a file manager app installed?", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupRecyclerView() {
        soundAdapter = SoundAdapter(
            onPlayClicked = { soundItem, position ->
                soundboardsViewModel.playSound(soundItem, position)
            },
            onDeleteClicked = { soundItem ->
                showDeleteConfirmationDialog(soundItem)
            }
        )
        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = soundAdapter
            val spacingInPixels = resources.getDimensionPixelSize(R.dimen.grid_spacing)
            if (itemDecorationCount > 0) {
                removeItemDecorationAt(0)
            }
            addItemDecoration(GridSpacingItemDecoration(2, spacingInPixels, true))
        }
    }

    private fun setupObservers() {
        soundboardsViewModel.soundItems.observe(viewLifecycleOwner) { items ->
            Log.d("SoundFragment", "Observer received items: ${items.size}")
            soundAdapter.submitList(items.toList()) // Pastikan ini aman jika items null
        }

        soundboardsViewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                soundboardsViewModel.consumeToastMessage()
            }
        }

        soundboardsViewModel.closeDialogEvent.observe(viewLifecycleOwner, EventObserver { unitContent: Unit -> // Tentukan tipe Unit secara eksplisit
            alertDialog?.dismiss()
            alertDialog = null
            selectedAudioUri = null
            selectedFileInfoTextView?.text = "No file selected"
            Log.d("SoundFragment", "closeDialogEvent observed and handled by EventObserver")
        })
    }

    private fun showDeleteConfirmationDialog(soundItem: SoundItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Sound")
            .setMessage("Are you sure you want to delete '${soundItem.title}'?")
            .setPositiveButton("Yes") { _, _ ->
                soundboardsViewModel.deleteSound(soundItem)
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onBottomNavHeightCalculated(height: Int) {
        if (!isAdded || _binding == null) return
        Log.d("SoundboardsFragment", "onBottomNavHeightCalculated: height $height")

        if (height > 0) {
            currentBottomNavHeight = height
            val systemGestureInsetsBottom = view?.let {
                ViewCompat.getRootWindowInsets(it)?.getInsets(WindowInsetsCompat.Type.systemGestures())?.bottom ?: 0
            } ?: 0
            val effectiveBottomNavHeight = currentBottomNavHeight + systemGestureInsetsBottom
            adjustViewsForBottomInsets(effectiveBottomNavHeight, currentImeHeight)
        }
    }

    override fun onResume() {
        super.onResume()
        activityCallback?.requestBottomNavHeight(this)
        view?.let { ViewCompat.requestApplyInsets(it) }
        if (isAdded && _binding != null && (currentBottomNavHeight > 0 || currentImeHeight >= 0)) {
            val systemGestureInsetsBottom = view?.let {
                ViewCompat.getRootWindowInsets(it)?.getInsets(WindowInsetsCompat.Type.systemGestures())?.bottom ?: 0
            } ?: 0
            val effectiveBottomNavHeight = currentBottomNavHeight + systemGestureInsetsBottom
            adjustViewsForBottomInsets(effectiveBottomNavHeight, currentImeHeight)
        }
        // Panggil kembali loadCurrentUserProfileImageToHeader di onResume jika ingin update gambar profil setiap kali fragment resume
        // if (currentUserId != null) {
        //     loadCurrentUserProfileImageToHeader()
        // }
    }

    override fun onStop() {
        super.onStop()
        soundboardsViewModel.stopAnyPlayingSound()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activityCallback?.clearBottomNavHeightListener(this)
        alertDialog?.dismiss()
        binding.recyclerView.adapter = null
        _binding = null
        Log.d("SoundboardsFragment", "onDestroyView called")
    }

    override fun onDetach() {
        super.onDetach()
        activityCallback = null
    }
}