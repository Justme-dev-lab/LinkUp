package com.example.linkup

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns // Untuk validasi email
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar // Impor ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.linkup.model.Users // Impor data class Users Anda
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.database.FirebaseDatabase

// Definisikan URL sebagai konstanta jika tidak menggunakan google-services.json secara default
object FirebaseConstants {
    const val DATABASE_URL = "https://linkup-3b210-default-rtdb.asia-southeast1.firebasedatabase.app"
    const val DEFAULT_PROFILE_IMAGE_URL = "https://firebasestorage.googleapis.com/v0/b/linkup-3b210.firebasestorage.app/o/profile.png?alt=media&token=b7f14feb-eff2-4cc4-92a9-de46b3dbf428"
}

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    // Hapus inisialisasi database di sini jika Anda ingin instance spesifik per operasi
    // atau gunakan instance dari FirebaseConstants

    private lateinit var etUsername: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnRegister: Button // Buat jadi properti kelas untuk disable saat loading
    private lateinit var progressBar: ProgressBar // Tambahkan ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register) // Pastikan layout Anda punya ProgressBar dengan ID progressBarRegist
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()

        // Initialize views
        etUsername = findViewById(R.id.UsernameRegist)
        etEmail = findViewById(R.id.EmailRegister)
        etPassword = findViewById(R.id.PasswordRegister)
        btnRegister = findViewById(R.id.Registbtn) // Inisialisasi properti kelas
        val btnToLogin = findViewById<Button>(R.id.toLogin)
        progressBar = findViewById(R.id.progressBarRegist) // Asumsi ID ini ada di XML Anda

        btnRegister.setOnClickListener {
            registerUser()
        }

        btnToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish() // Selesaikan RegisterActivity agar tidak kembali ke sini dengan tombol back
        }
    }

    private fun registerUser() {
        val username = etUsername.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString() // Password sebaiknya tidak di-trim

        // Validasi Input
        if (username.isEmpty()) {
            etUsername.error = "Username tidak boleh kosong"
            etUsername.requestFocus()
            return
        }
        if (email.isEmpty()) {
            etEmail.error = "Email tidak boleh kosong"
            etEmail.requestFocus()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Format email tidak valid"
            etEmail.requestFocus()
            return
        }
        if (password.isEmpty()) {
            etPassword.error = "Password tidak boleh kosong"
            etPassword.requestFocus()
            return
        }
        if (password.length < 6) {
            etPassword.error = "Password minimal 6 karakter"
            etPassword.requestFocus()
            return
        }

        // Tampilkan ProgressBar dan disable tombol
        progressBar.visibility = View.VISIBLE
        btnRegister.isEnabled = false
        Log.d("RegisterActivity", "Memulai proses registrasi untuk email: $email")

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task -> // Tambahkan 'this' untuk context Activity
                progressBar.visibility = View.GONE // Sembunyikan ProgressBar setelah selesai
                btnRegister.isEnabled = true // Aktifkan kembali tombol

                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    if (firebaseUser != null) {
                        val firebaseUserId = firebaseUser.uid
                        Log.d("RegisterActivity", "Registrasi Firebase Auth berhasil, UID: $firebaseUserId")

                        // Menggunakan data class Users
                        val newUser = Users(
                            uid = firebaseUserId,
                            username = username,
                            profile = FirebaseConstants.DEFAULT_PROFILE_IMAGE_URL,
                            search = username.lowercase()
                            // Jika Anda menambahkan email ke model Users:
                            // email = email
                        )

                        // Dapatkan instance database yang benar menuju node pengguna
                        val userDatabaseReference = FirebaseDatabase.getInstance(FirebaseConstants.DATABASE_URL)
                            .getReference("users")
                            .child(firebaseUserId)

                        Log.d("RegisterActivity", "Menulis data pengguna ke Realtime Database...")
                        userDatabaseReference.setValue(newUser)
                            .addOnCompleteListener { dbTask ->
                                if (dbTask.isSuccessful) {
                                    Toast.makeText(this, "Registrasi berhasil!", Toast.LENGTH_SHORT).show()
                                    Log.d("RegisterActivity", "Penulisan ke Realtime Database berhasil.")
                                    val intent = Intent(this, LoginActivity::class.java)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(intent)
                                    finish()
                                } else {
                                    Log.e("RegisterActivity", "Gagal menulis ke Realtime Database: ${dbTask.exception?.message}")
                                    // Pengguna sudah dibuat di Auth, tapi gagal simpan ke DB.
                                    // Anda mungkin ingin memberi tahu pengguna atau mencoba lagi.
                                    // Untuk sekarang, kita tampilkan error saja.
                                    Toast.makeText(this, "Gagal menyimpan data pengguna: ${dbTask.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                    } else {
                        // Kasus yang jarang terjadi jika task successful tapi currentUser null
                        Log.e("RegisterActivity", "Registrasi Auth berhasil tapi currentUser null.")
                        Toast.makeText(this, "Terjadi kesalahan saat registrasi.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.w("RegisterActivity", "Registrasi Firebase Auth gagal: ${task.exception?.message}")
                    // Tangani error registrasi yang lebih spesifik
                    try {
                        throw task.exception!!
                    } catch (_: FirebaseAuthWeakPasswordException) {
                        etPassword.error = "Password terlalu lemah."
                        etPassword.requestFocus()
                        Toast.makeText(this, "Password terlalu lemah.", Toast.LENGTH_LONG).show()
                    } catch (_: FirebaseAuthUserCollisionException) {
                        etEmail.error = "Email sudah terdaftar."
                        etEmail.requestFocus()
                        Toast.makeText(this, "Email sudah terdaftar. Silakan login atau gunakan email lain.", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Registrasi gagal: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .addOnFailureListener(this) { e -> // Listener kegagalan tambahan
                progressBar.visibility = View.GONE
                btnRegister.isEnabled = true
                Log.e("RegisterActivity", "addOnFailureListener: Registrasi Firebase Auth gagal: ${e.message}")
                Toast.makeText(this, "Registrasi gagal (listener): ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}