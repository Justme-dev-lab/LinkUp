package com.example.linkup

import android.content.Intent
import android.graphics.drawable.Drawable // Import Drawable untuk RequestListener
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource // Import DataSource
import com.bumptech.glide.load.engine.GlideException // Import GlideException
import com.bumptech.glide.request.RequestListener // Import RequestListener
import com.bumptech.glide.request.target.Target // Import Target
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var currentUser: FirebaseUser? = null
    private lateinit var profileImageView: ImageView
    private lateinit var nameTextView: TextView

    // Konstanta untuk Tag Log
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

        // Inisialisasi Views
        try {
            profileImageView = findViewById(R.id.profileImage)
            nameTextView = findViewById(R.id.namaTextView) // Pastikan ID ini benar di XML Anda
            Log.d(TAG, "onCreate: Views initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Error initializing views. Check your XML IDs (profileImage, namaTextView).", e)
            // Anda mungkin ingin menampilkan pesan error atau menutup activity jika view penting tidak ditemukan
            finish() // Contoh: Tutup activity jika view utama tidak ada
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

        val logoutButton: Button = findViewById(R.id.logoutbtn)
        logoutButton.setOnClickListener {
            Log.d(TAG, "Logout button clicked.")
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finishAffinity() // Gunakan finishAffinity() untuk membersihkan semua activity di atas LoginActivity
        }

        if (currentUser != null) {
            loadUserProfile()
        } else {
            Log.e(TAG, "onCreate: Current user is null. Cannot load profile. Redirecting to Login.")
            // Arahkan ke LoginActivity jika pengguna tidak login
            val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun loadUserProfile() {
        val userId = currentUser?.uid
        Log.d(TAG, "loadUserProfile: Called for userID: $userId")

        if (userId == null) {
            Log.e(TAG, "loadUserProfile: userId is null, cannot proceed.")
            // Mungkin tampilkan pesan error default atau kembali
            if (::nameTextView.isInitialized) {
                nameTextView.text = "Error: Pengguna tidak valid"
            }
            if (::profileImageView.isInitialized) {
                profileImageView.setImageResource(R.drawable.ic_profile_error) // Atau placeholder default
            }
            return
        }

        val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
        Log.d(TAG, "loadUserProfile: Attempting to read from Firebase path: ${userRef.toString()}")

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Firebase onDataChange: Snapshot exists: ${snapshot.exists()}")
                if (snapshot.exists()) {
                    Log.d(TAG, "Firebase onDataChange: Snapshot data: ${snapshot.value}") // LOG SEMUA DATA SNAPSHOT

                    val username = snapshot.child("username").getValue(String::class.java)
                    val profileImageUrl = snapshot.child("profile").getValue(String::class.java) // Kunci "profile"

                    Log.d(TAG, "Firebase onDataChange: Extracted Username: $username")
                    Log.d(TAG, "Firebase onDataChange: Extracted Profile Image URL: $profileImageUrl")

                    if (::nameTextView.isInitialized) {
                        nameTextView.text = username ?: "Nama Tidak Tersedia"
                    } else {
                        Log.w(TAG, "Firebase onDataChange: nameTextView is not initialized when trying to set username.")
                    }

                    if (!profileImageUrl.isNullOrEmpty()) {
                        Log.d(TAG, "Glide: Attempting to load image URL: $profileImageUrl")
                        Glide.with(this@ProfileActivity)
                            .load(profileImageUrl)
                            .placeholder(R.drawable.ic_profile) // Pastikan drawable ini ada
                            .error(R.drawable.ic_profile_error) // Pastikan drawable ini ada
                            .circleCrop()
                            .listener(object : RequestListener<Drawable> {
                                override fun onLoadFailed(
                                    e: GlideException?,
                                    model: Any?,
                                    target: Target<Drawable?>,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    Log.e(TAG, "Glide onLoadFailed for URL: $model", e)
                                    return false
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
                        Log.w(TAG, "Glide: Profile image URL from DB is null or empty. Setting placeholder.")
                        if (::profileImageView.isInitialized) {
                            profileImageView.setImageResource(R.drawable.ic_profile)
                        }
                    }
                } else {
                    Log.w(TAG, "Firebase onDataChange: User data not found in DB for UID: $userId")
                    if (::nameTextView.isInitialized) {
                        nameTextView.text = "Data Pengguna Tidak Ditemukan"
                    }
                    if (::profileImageView.isInitialized) {
                        profileImageView.setImageResource(R.drawable.ic_profile) // Atau gambar error khusus
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
        // Anda tidak menggunakan ValueEventListener yang berkelanjutan, jadi tidak perlu remove listener di sini.
        // Namun, baik untuk membatalkan permintaan Glide jika activity dihancurkan
        if (::profileImageView.isInitialized) { // Cek jika sudah diinisialisasi
            try {
                if (isDestroyed || isFinishing) { // Pastikan activity benar-benar dihancurkan
                    Glide.with(applicationContext).clear(profileImageView) // Atau this@ProfileActivity jika masih valid
                    Log.d(TAG, "onDestroy: Cleared Glide request for profileImageView.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "onDestroy: Error clearing Glide request.", e)
            }
        }
    }
}