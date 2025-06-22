package com.example.linkup

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.linkup.databinding.ActivityMainBinding
import com.example.linkup.ui.chats.BottomNavHeightListener
import com.example.linkup.ui.chats.ChatsFragment // Import ChatsFragment untuk ActivityCallback
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity(), ChatsFragment.ActivityCallback { // Implementasikan ActivityCallback

    var refUsers: DatabaseReference? = null
    var firebaseUser: FirebaseUser? = null


    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var navController: NavController

    // List untuk menyimpan listener (fragment) yang tertarik pada tinggi BottomNav
    private val bottomNavHeightListeners = mutableListOf<BottomNavHeightListener>()
    private var knownBottomNavHeight: Int = 0 // Simpan tinggi yang sudah diketahui

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        // Pastikan root layout di activity_main.xml memiliki android:fitsSystemWindows="true"
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView // Pastikan ID "navView" benar di XML Anda

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController

        navView.setupWithNavController(navController)

        // [TAMBAHAN] Set intent ke ProfileActivity saat tombol profileButton diklik
        binding.root.findViewById<View>(R.id.profileButton)?.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }
        // [TAMBAHAN SELESAI]

        // Dapatkan tinggi BottomNavigationView setelah di-layout
        navView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // Hapus listener agar tidak dipanggil berkali-kali jika layout berubah lagi
                // Namun, kita mungkin ingin mempertahankannya jika tinggi BottomNav bisa berubah dinamis
                // Untuk kasus umum, cukup sekali setelah layout awal.
                // Jika ingin lebih dinamis, jangan remove listener, tapi pastikan logika tidak berulang tanpa perlu.
                // navView.viewTreeObserver.removeOnGlobalLayoutListener(this) // Komentari ini jika perlu update dinamis

                val currentHeight = navView.height
                if (currentHeight > 0 && knownBottomNavHeight != currentHeight) {
                    knownBottomNavHeight = currentHeight
                    Log.d("MainActivity", "BottomNav height calculated: $knownBottomNavHeight")
                    // Beri tahu semua listener (fragment) yang terdaftar
                    // Buat salinan list untuk menghindari ConcurrentModificationException jika listener menghapus dirinya sendiri
                    ArrayList(bottomNavHeightListeners).forEach { listener ->
                        listener.onBottomNavHeightCalculated(knownBottomNavHeight)
                    }
                }
            }
        })
    }

    // Implementasi metode dari ChatsFragment.ActivityCallback
    override fun requestBottomNavHeight(listener: BottomNavHeightListener) {
        if (!bottomNavHeightListeners.contains(listener)) {
            bottomNavHeightListeners.add(listener)
            Log.d("MainActivity", "Listener added: $listener, Total listeners: ${bottomNavHeightListeners.size}")
        }
        // Jika tinggi sudah diketahui saat fragment mendaftar, langsung kirim
        if (knownBottomNavHeight > 0) {
            listener.onBottomNavHeightCalculated(knownBottomNavHeight)
        } else {
            // Jika belum diketahui, trigger pengukuran ulang jika navView sudah ada
            // Ini berguna jika fragment mendaftar sebelum onGlobalLayout pertama kali terpanggil
            binding.navView.post {
                if (binding.navView.height > 0 && knownBottomNavHeight != binding.navView.height) {
                    knownBottomNavHeight = binding.navView.height
                    listener.onBottomNavHeightCalculated(knownBottomNavHeight)
                } else if (binding.navView.height > 0) { // Jika tingginya sama, tapi listener baru
                    listener.onBottomNavHeightCalculated(binding.navView.height)
                }
            }
        }
    }

    override fun clearBottomNavHeightListener(listener: BottomNavHeightListener) {
        val removed = bottomNavHeightListeners.remove(listener)
        if (removed) {
            Log.d("MainActivity", "Listener removed: $listener, Total listeners: ${bottomNavHeightListeners.size}")
        }
    }

    // Komentari atau hapus metode onSupportNavigateUp() jika Anda tidak menggunakan ActionBar
    // yang di-setup dengan NavigationController.
    /*
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
    */
}