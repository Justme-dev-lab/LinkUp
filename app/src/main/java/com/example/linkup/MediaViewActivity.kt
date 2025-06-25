package com.example.linkup // Sesuaikan dengan package Anda

import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
// Hapus import androidx.glance.visibility karena tidak digunakan dan bisa menyebabkan konflik
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.linkup.databinding.ActivityMediaViewBinding // Pastikan ViewBinding diaktifkan
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException // Import yang benar untuk PlaybackException
import com.google.android.exoplayer2.Player
// import com.google.android.exoplayer2.ui.PlayerView // Jika menggunakan ExoPlayer lama (sebelum StyledPlayerView)
// Untuk ExoPlayer versi lebih baru, StyledPlayerView sudah di-handle oleh ViewBinding jika ID-nya benar


class MediaViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMediaViewBinding
    private var exoPlayer: ExoPlayer? = null

    companion object {
        const val EXTRA_MEDIA_URL = "extra_media_url"
        const val EXTRA_MEDIA_TYPE = "extra_media_type" // "image" atau "video"
        const val EXTRA_MEDIA_TITLE = "extra_media_title" // Opsional: untuk judul di toolbar
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()

        val mediaUrl = intent.getStringExtra(EXTRA_MEDIA_URL)
        val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE)
        val mediaTitle = intent.getStringExtra(EXTRA_MEDIA_TITLE)

        supportActionBar?.title = if (!mediaTitle.isNullOrEmpty()) {
            mediaTitle
        } else {
            if (mediaType == "image") getString(R.string.view_image_title) else getString(R.string.view_video_title)
        }


        if (mediaUrl.isNullOrEmpty()) {
            Toast.makeText(this, R.string.media_url_not_available, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        when (mediaType) {
            "image" -> loadImage(mediaUrl)
            "video" -> loadVideoWithExoPlayer(mediaUrl)
            else -> {
                Toast.makeText(this, R.string.unsupported_media_type, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarMediaView)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    private fun loadImage(url: String) {
        // Sesuaikan dengan ID ImageView Anda di layout
        binding.imageViewFullscreen.visibility = View.VISIBLE
        binding.exoplayerViewFullscreen.visibility = View.GONE
        // Jika Anda memiliki VideoView dan tidak menggunakannya untuk gambar, pastikan juga disembunyikan
        // binding.videoViewFullscreen.visibility = View.GONE
        binding.progressBarMedia.visibility = View.VISIBLE

        Glide.with(this)
            .load(url)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.progressBarMedia.visibility = View.GONE
                    Toast.makeText(this@MediaViewActivity, R.string.failed_to_load_image, Toast.LENGTH_SHORT).show()
                    // Anda mungkin ingin menampilkan ikon error di ImageView di sini
                    binding.imageViewFullscreen.setImageResource(R.drawable.ic_broken_image)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.progressBarMedia.visibility = View.GONE
                    return false
                }
            })
            .error(R.drawable.ic_broken_image) // Sediakan drawable ini
            .into(binding.imageViewFullscreen) // Targetkan ImageView biasa
    }

    private fun initializeExoPlayer() {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(this).build()
            binding.exoplayerViewFullscreen.player = exoPlayer
            exoPlayer?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> binding.progressBarMedia.visibility = View.VISIBLE
                        Player.STATE_READY -> binding.progressBarMedia.visibility = View.GONE
                        Player.STATE_ENDED -> { /* Video Selesai, Anda bisa tambahkan logika di sini */ }
                        Player.STATE_IDLE -> { /* Player Idle */ }
                    }
                }
                override fun onPlayerError(error: PlaybackException) { // Menggunakan PlaybackException yang benar
                    super.onPlayerError(error)
                    binding.progressBarMedia.visibility = View.GONE
                    Toast.makeText(this@MediaViewActivity, "${getString(R.string.error_playing_video)}: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun loadVideoWithExoPlayer(url: String) {
        binding.imageViewFullscreen.visibility = View.GONE
        // Jika Anda masih memiliki VideoView di layout dan tidak digunakan, pastikan disembunyikan
        // binding.videoViewFullscreen.visibility = View.GONE
        binding.exoplayerViewFullscreen.visibility = View.VISIBLE
        binding.progressBarMedia.visibility = View.VISIBLE

        initializeExoPlayer()

        val mediaItem = MediaItem.fromUri(Uri.parse(url))
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true
    }

    // Fungsi loadVideoWithVideoView masih ada, bisa Anda hapus jika hanya menggunakan ExoPlayer
    // atau biarkan jika sewaktu-waktu ingin menggunakannya kembali.

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()
        // Untuk API 24+, ExoPlayer direkomendasikan untuk diinisialisasi/dipulihkan di onStart
        // Namun, karena kita memanggil initializeExoPlayer() di loadVideoWithExoPlayer,
        // kita perlu memastikan itu tidak double initialization.
        // Jika exoPlayer sudah ada dan mediaType adalah video, kita bisa langsung play.
        if (exoPlayer != null && intent.getStringExtra(EXTRA_MEDIA_TYPE) == "video") {
            exoPlayer?.playWhenReady = true
        } else if (intent.getStringExtra(EXTRA_MEDIA_TYPE) == "video" && exoPlayer == null) {
            // Jika player belum ada (misal, activity dibuat ulang), inisialisasi lagi
            intent.getStringExtra(EXTRA_MEDIA_URL)?.let { loadVideoWithExoPlayer(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        // Sembunyikan status bar dan navigation bar untuk pengalaman fullscreen video (opsional)
        // if (intent.getStringExtra(EXTRA_MEDIA_TYPE) == "video") {
        //      hideSystemUi()
        // }

        // Pastikan player melanjutkan jika sebelumnya dipause karena onPause,
        // dan hanya jika player sudah diinisialisasi.
        if (exoPlayer != null) {
            exoPlayer?.playWhenReady = true
        }
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.playWhenReady = false // Pause player agar tidak berjalan di background
        // Untuk API 23 ke bawah, rilis player di onPause karena onStop tidak selalu dijamin terpanggil
        if (android.os.Build.VERSION.SDK_INT <= 23) {
            releasePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        // Untuk API 24+, rilis player di onStop
        if (android.os.Build.VERSION.SDK_INT > 23) {
            releasePlayer()
        }
    }

    private fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}