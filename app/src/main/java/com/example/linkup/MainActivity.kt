package com.example.linkup

import android.content.Intent
import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
// Hapus import yang tidak perlu jika hanya menggunakan BottomNavigationView
// import androidx.navigation.ui.AppBarConfiguration
// import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.linkup.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // HAPUS setSupportActionBar(binding.toolbar) jika Toolbar tidak ada di binding
        // atau jika Anda tidak menggunakan ViewBinding dan Toolbar sudah dihapus dari XML

        val navView: BottomNavigationView = binding.navView

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController

        // HAPUS BAGIAN AppBarConfiguration dan setupActionBarWithNavController
        /*
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_chats,
                R.id.navigation_soundboards,
                R.id.navigation_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        */

        // Baris ini PENTING untuk BottomNavigationView
        navView.setupWithNavController(navController)
    }

    // HAPUS metode onSupportNavigateUp() jika tidak ada ActionBar
    /*
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
    */
}