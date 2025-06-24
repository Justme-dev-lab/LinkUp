package com.example.linkup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.example.linkup.model.Users
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

class EditProfileActivity : AppCompatActivity() {

    private val IMAGE_PICK_CODE = 1000
    private lateinit var profileImageView: ImageView
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_edit_profile)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val simpanButton = findViewById<Button>(R.id.simpanbtn)
        simpanButton.setOnClickListener {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener

            if (imageUri != null) {
                val storageRef = FirebaseStorage.getInstance()
                    .getReference("profile_images/$uid.jpg")

                storageRef.putFile(imageUri!!)
                    .addOnSuccessListener {
                        // ✅ Ambil download URL
                        storageRef.downloadUrl.addOnSuccessListener { uri ->
                            val profileUrl = uri.toString()
                            // ✅ Kirim URL ke database
                            updateUserProfile(profileUrl)
                        }.addOnFailureListener {
                            Toast.makeText(this, "Gagal mendapatkan URL gambar", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Gagal upload gambar", Toast.LENGTH_SHORT).show()
                    }
            } else {
                // ✅ Jika tidak ganti gambar, jangan timpa field profile
                updateUserProfile(null)
            }
        }

        profileImageView = findViewById(R.id.editProfileImage)

        profileImageView.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, IMAGE_PICK_CODE)
        }

        loadUserData()

    }
    private fun loadUserData() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val userRef = FirebaseDatabase.getInstance("https://linkup-3b210-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("users")
            .child(uid)

        userRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val user = snapshot.getValue(Users::class.java)
                user?.let {
                    findViewById<EditText>(R.id.namaEditText).setText(it.username)
                    findViewById<EditText>(R.id.aboutEditText).setText(it.about)
                    findViewById<EditText>(R.id.phoneEditText).setText(it.phone)

                    // Jika kamu ingin juga munculkan gambar lama
                    if (it.profile.isNotEmpty()) {
                        Glide.with(this)
                            .load(it.profile)
                            .placeholder(R.drawable.ic_profile)
                            .error(R.drawable.ic_profile_error)
                            .circleCrop()
                            .into(profileImageView)
                    }
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Gagal memuat data profil", Toast.LENGTH_SHORT).show()
        }
    }
    private fun updateUserProfile(profileUrl: String?) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val name = findViewById<EditText>(R.id.namaEditText).text.toString()
        val about = findViewById<EditText>(R.id.aboutEditText).text.toString()
        val phone = findViewById<EditText>(R.id.phoneEditText).text.toString()

        val updates = mutableMapOf<String, Any>()
        updates["username"] = name
        updates["about"] = about
        updates["phone"] = phone

        if (profileUrl != null) {
            updates["profile"] = profileUrl
        }

        FirebaseDatabase.getInstance("https://linkup-3b210-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("users")
            .child(uid)
            .updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Profil berhasil diperbarui", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal menyimpan data", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == IMAGE_PICK_CODE && resultCode == RESULT_OK && data != null) {
            imageUri = data.data
            profileImageView.setImageURI(imageUri) // tampilkan gambar dipilih ke UI
        }
    }
}