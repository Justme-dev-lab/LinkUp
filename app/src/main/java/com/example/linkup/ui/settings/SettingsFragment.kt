// ui/settings/SettingsFragment.kt
package com.example.linkup.ui.settings

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.linkup.ProfileActivity // Asumsi Activity profil sudah ada
// Import Activity tujuan (buat Activity kosong jika belum ada)
import com.example.linkup.AccountActivity
import com.example.linkup.PrivacyActivity
import com.example.linkup.NotificationActivity // Ganti dengan nama Activity yang benar
import com.example.linkup.StorageActivity     // Ganti dengan nama Activity yang benar
import com.example.linkup.HelpActivity        // Ganti dengan nama Activity yang benar
import com.example.linkup.R
import com.example.linkup.databinding.FragmentSettingsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!! // Hanya valid antara onCreateView dan onDestroyView

    private val settingsViewModel: SettingsViewModel by viewModels() // Jika Anda membuatnya

    private var firebaseUser: FirebaseUser? = null
    private var currentUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseUser = FirebaseAuth.getInstance().currentUser
        currentUserId = firebaseUser?.uid
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyStatusBarPaddingToHeader()
        setupClickListeners()

        if (currentUserId != null) {
            loadCurrentUserProfileImageToHeader()
        } else {
            Log.w("SettingsFragment", "User not logged in, cannot load profile image.")
            // Set gambar default jika user tidak login
            binding.profileButton.setImageResource(R.drawable.ic_profile)
        }
    }

    private fun applyStatusBarPaddingToHeader() {
        // ID header_title dari XML Anda
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerTitle) { headerView, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            headerView.updatePadding(top = systemBars.top)
            // Kembalikan WindowInsets yang sudah dimodifikasi agar tidak menghilangkan padding lain
            WindowInsetsCompat.Builder(insets).setInsets(
                WindowInsetsCompat.Type.systemBars(),
                androidx.core.graphics.Insets.of(systemBars.left, 0, systemBars.right, systemBars.bottom)
            ).build()
        }
    }

    private fun loadCurrentUserProfileImageToHeader() {
        val userId = currentUserId ?: return

        val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || _binding == null) return // Cek jika fragment masih ada

                if (snapshot.exists()) {
                    val profileImageUrl = snapshot.child("profile").getValue(String::class.java)
                    if (!profileImageUrl.isNullOrEmpty()) {
                        Glide.with(requireContext())
                            .load(profileImageUrl)
                            .placeholder(R.drawable.ic_profile)
                            .error(R.drawable.ic_profile_error) // Sediakan drawable ic_profile_error
                            .apply(RequestOptions.circleCropTransform())
                            .into(binding.profileButton) // ID ImageButton di header
                    } else {
                        binding.profileButton.setImageResource(R.drawable.ic_profile)
                        Log.w("SettingsFragment", "Profile image URL is null or empty.")
                    }
                } else {
                    binding.profileButton.setImageResource(R.drawable.ic_profile)
                    Log.w("SettingsFragment", "User data not found for profile image for UID: $userId")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isAdded || _binding == null) return
                Log.e("SettingsFragment", "Failed to load profile image.", error.toException())
                binding.profileButton.setImageResource(R.drawable.ic_profile_error)
            }
        })
    }

    private fun setupClickListeners() {
        // Listener untuk tombol profil di header
        binding.profileButton.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }

        // Listener untuk item menu
        binding.accountBtn.setOnClickListener {
            // Pastikan AccountActivity sudah ada
            startActivity(Intent(requireContext(), AccountActivity::class.java))
        }

        binding.privacyBtn.setOnClickListener {
            // Pastikan PrivacyActivity sudah ada
            startActivity(Intent(requireContext(), PrivacyActivity::class.java))
        }

        binding.notifBtn.setOnClickListener {
            // Pastikan NotificationActivity sudah ada
            startActivity(Intent(requireContext(), NotificationActivity::class.java))
        }

        binding.storageBtn.setOnClickListener {
            // Pastikan StorageActivity sudah ada
            startActivity(Intent(requireContext(), StorageActivity::class.java))
        }

        binding.helpBtn.setOnClickListener {
            // Pastikan HelpActivity sudah ada
            startActivity(Intent(requireContext(), HelpActivity::class.java))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Penting untuk menghindari memory leak
    }
}