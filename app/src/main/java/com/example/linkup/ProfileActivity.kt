package com.example.linkup

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

import android.widget.EditText
import android.widget.Toast

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.linkup.model.Users // <-- Tambahkan import untuk data class Users Anda
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ProfileActivity : AppCompatActivity() {


    private var isEditingAbout = false
    private var isEditingPhone = false

    private lateinit var auth: FirebaseAuth
    private var currentUser: FirebaseUser? = null
    private lateinit var profileImageView: ImageView
    private lateinit var nameTextView: TextView
    private lateinit var emailTextView: TextView

    private companion object {
        private const val TAG = "ProfileActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)
        Log.d(TAG, "onCreate: Activity created.")

        auth = FirebaseAuth.getInstance()
        currentUser = auth.currentUser
        Log.d(TAG, "onCreate: Current User UID: ${currentUser?.uid ?: "User is NULL"}")
        Log.d(TAG, "onCreate: Current User Email: ${currentUser?.email ?: "Email not available"}")

        try {
            profileImageView = findViewById(R.id.profileImage)
            nameTextView = findViewById(R.id.namaTextView)
            emailTextView = findViewById(R.id.emailValueTextView)
            Log.d(TAG, "onCreate: Views initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Error initializing views. Check your XML IDs (profileImage, namaTextView).", e)
            finish()
            return
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val back: Button = findViewById(R.id.profback)
        back.setOnClickListener {
            Log.d(TAG, "Back button clicked.")
            finish()
        }

        val editProfileButton: Button = findViewById(R.id.editprofilebtn)
        editProfileButton.setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)
            startActivity(intent)

        }
        val logoutButton: Button = findViewById(R.id.logoutbtn)
        logoutButton.setOnClickListener {
            Log.d(TAG, "Logout button clicked.")
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finishAffinity()
        }

        if (currentUser != null) {
            // Langsung set email di sini karena sudah tersedia dari currentUser
            if (::emailTextView.isInitialized) {
                emailTextView.text = currentUser?.email ?: "Email Tidak Tersedia"
            } else {
                Log.w(TAG, "onCreate: emailValueTextView is not initialized when trying to set email.")
            }
            loadUserProfile()
        // Lanjutkan memuat data lain dari database jika perlu
        } else {
            Log.e(TAG, "onCreate: Current user is null. Cannot load profile. Redirecting to Login.")
            val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Refreshing user profile...")
        loadUserProfile()
    }
    private fun loadUserProfile() {
        val userId = currentUser?.uid
        Log.d(TAG, "loadUserProfile: Called for userID: $userId")

        if (userId == null)
        {
            Log.e(TAG, "loadUserProfile: userId is null, cannot proceed.")
            if (::nameTextView.isInitialized) {
                nameTextView.text = "Error: Pengguna tidak valid"
            }
            if (::profileImageView.isInitialized) {
                profileImageView.setImageResource(R.drawable.ic_profile_error)
            }
            return
        }

        // Gunakan URL database dari konstanta jika ada, atau biarkan default jika google-services.json benar
        val userRef = FirebaseDatabase.getInstance("https://linkup-3b210-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("users").child(userId)
        Log.d(TAG, "loadUserProfile: Attempting to read from Firebase path: ${userRef.toString()}")

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Firebase onDataChange: Snapshot exists: ${snapshot.exists()}")
                if (snapshot.exists()) {
                    // Deserialisasi DataSnapshot ke objek Users
                    val user = snapshot.getValue(Users::class.java)
                    Log.d(TAG, "Firebase onDataChange: Deserialized User object: $user")

                    if (user != null) {

                        val aboutText: TextView = findViewById(R.id.aboutValueText)
                        val phoneText: TextView = findViewById(R.id.phoneValueText)

                        aboutText.text = user.about.ifEmpty { "Belum diisi" }
                        phoneText.text = user.phone.ifEmpty { "Belum diisi" }

                        if (::nameTextView.isInitialized) {
                            nameTextView.text = user.username.ifEmpty { "Nama Tidak Tersedia" }
                        }

                        else {
                            Log.w(TAG, "Firebase onDataChange: nameTextView is not initialized when trying to set username.")
                        }

                        if (user.profile.isNotEmpty()) {
                            Log.d(TAG, "Glide: Attempting to load image URL: ${user.profile}")
                            if (::profileImageView.isInitialized) { // Pastikan ImageView diinisialisasi sebelum digunakan Glide
                                Glide.with(this@ProfileActivity)
                                    .load(user.profile)
                                    .placeholder(R.drawable.ic_profile)
                                    .error(R.drawable.ic_profile_error)
                                    .circleCrop()
                                    .listener(object : RequestListener<Drawable> {
                                        override fun onLoadFailed(
                                            e: GlideException?,
                                            model: Any?,
                                            target: Target<Drawable?>,
                                            isFirstResource: Boolean
                                        ): Boolean {
                                            Log.e(TAG, "Glide onLoadFailed for URL: $model", e)
                                            // Set gambar error jika Glide gagal, meskipun sudah ada .error()
                                            profileImageView.setImageResource(R.drawable.ic_profile_error)
                                            return false // Mengembalikan false agar error() tetap diproses jika perlu
                                        }

                                        override fun onResourceReady(
                                            resource: Drawable,
                                            model: Any,
                                            target: Target<Drawable?>?,
                                            dataSource: DataSource,
                                            isFirstResource: Boolean
                                        ): Boolean {
                                            Log.d(TAG, "Glide onResourceReady for URL: $model. DataSource: $dataSource")
                                            return false
                                        }
                                    })
                                    .into(profileImageView)
                            } else {
                                Log.w(TAG, "Glide: profileImageView is not initialized when trying to load image.")
                            }
                        } else {
                            Log.w(TAG, "Glide: Profile image URL from User object is empty. Setting placeholder.")
                            if (::profileImageView.isInitialized) {
                                profileImageView.setImageResource(R.drawable.ic_profile)
                            }
                        }
                    } else {
                        Log.w(TAG, "Firebase onDataChange: Failed to deserialize snapshot to User object for UID: $userId")
                        if (::nameTextView.isInitialized) {
                            nameTextView.text = "Data Pengguna Tidak Valid"
                        }
                        if (::profileImageView.isInitialized) {
                            profileImageView.setImageResource(R.drawable.ic_profile_error)
                        }
                    }
                } else {
                    Log.w(TAG, "Firebase onDataChange: User data not found in DB for UID: $userId")
                    if (::nameTextView.isInitialized) {
                        nameTextView.text = "Data Pengguna Tidak Ditemukan"
                    }
                    if (::profileImageView.isInitialized) {
                        profileImageView.setImageResource(R.drawable.ic_profile)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase onCancelled: Error loading user data. Code: ${error.code}, Message: ${error.message}", error.toException())
                if (::nameTextView.isInitialized) {
                    nameTextView.text = "Gagal Memuat Data"
                }
                if (::profileImageView.isInitialized) {
                    profileImageView.setImageResource(R.drawable.ic_profile_error)
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Activity destroyed.")
        if (::profileImageView.isInitialized) {
            try {
                // Pastikan untuk clear Glide hanya jika activity benar-benar dihancurkan
                // dan context masih valid. Menggunakan applicationContext lebih aman di onDestroy.
                if (!isFinishing && !isChangingConfigurations) {
                    // Jangan clear jika hanya reorientasi
                    return
                }
                Glide.with(applicationContext).clear(profileImageView)
                Log.d(TAG, "onDestroy: Cleared Glide request for profileImageView.")
            } catch (e: Exception) {
                Log.e(TAG, "onDestroy: Error clearing Glide request.", e)
            }
        }
    }

}