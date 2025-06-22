package com.example.linkup

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var firebaseUserId: String = ""

    private lateinit var etUsername: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance("https://linkup-3b210-default-rtdb.asia-southeast1.firebasedatabase.app").reference

        val btnRegister = findViewById<Button>(R.id.Registbtn)
        val btnToLogin = findViewById<Button>(R.id.toLogin)

        btnRegister.setOnClickListener {
            registerUser()
        }

        // Initialize views
        etUsername = findViewById(R.id.UsernameRegist)
        etEmail = findViewById(R.id.EmailRegister)
        etPassword = findViewById(R.id.PasswordRegister)



        btnToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun registerUser() {

        Log.d("RegisterDebug", "Registrasi berhasil, UID: $firebaseUserId")
        Log.d("RegisterDebug", "Menulis ke database...")

        val username = etUsername.text.toString()
        val email = etEmail.text.toString()
        val password = etPassword.text.toString()

        if (username == "") {
            Toast.makeText(this@RegisterActivity, "Harap isi username", Toast.LENGTH_LONG).show()
        } else if (email == "") {
            Toast.makeText(this@RegisterActivity, "Harap isi email", Toast.LENGTH_LONG).show()
        } else if (password == "") {
            Toast.makeText(this@RegisterActivity, "Harap isi password", Toast.LENGTH_LONG).show()
        } else {
            auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    firebaseUserId = auth.currentUser!!.uid
                    database = FirebaseDatabase.getInstance("https://linkup-3b210-default-rtdb.asia-southeast1.firebasedatabase.app").reference.child("users")
                        .child(firebaseUserId)

                    val userHashMap = HashMap<String, Any>()
                    userHashMap["uid"] = firebaseUserId
                    userHashMap["username"] = username
                    userHashMap["profile"] =
                        "https://firebasestorage.googleapis.com/v0/b/linkup-3b210.firebasestorage.app/o/profile.png?alt=media&token=b7f14feb-eff2-4cc4-92a9-de46b3dbf428"
                    userHashMap["search"] = username.lowercase()

                    database.setValue(userHashMap).addOnCompleteListener { dbtask ->
                        if (dbtask.isSuccessful) {
                            Toast.makeText(
                                this@RegisterActivity,
                                "Registrasi berhasil!",
                                Toast.LENGTH_LONG
                            ).show()

                            val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(
                                this@RegisterActivity,
                                "Error Message: ${dbtask.exception?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                }
            }
        }
    }
}