package com.example.linkup // Sesuaikan package Anda

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
// HAPUS impor ini jika ada dan tidak digunakan
// import androidx.compose.ui.semantics.text
// import android.provider.Settings // Mungkin tidak lagi diperlukan jika Anda selalu mengambil dari RingtoneManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.linkup.databinding.ActivityNotificationBinding // Pastikan ViewBinding diaktifkan
// Impor untuk NotificationSettings tidak diperlukan secara langsung di Activity ini
// karena interaksi terjadi melalui ViewModel

class NotificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationBinding
    private val viewModel: NotificationViewModel by viewModels()

    private val ringtonePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                val toneName = uri?.let { viewModel.getRingtoneName(this, it) } ?: "Silent" // Atau "Default" jika Silent tidak dipilih
                viewModel.updateNotificationTone(uri, toneName, this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Top Bar menggunakan View Binding
        binding.topBarLayout.topBarTitle.text = "Notifications"
        binding.topBarLayout.backButton.setOnClickListener { finish() }

        viewModel.settings.observe(this) { settings ->
            // Pastikan settings tidak null sebelum mengakses propertinya
            settings?.let {
                binding.textViewNotificationTone.text = it.toneName ?: "Default"
                binding.radioGroupVibrate.check(
                    if (it.vibrate) R.id.radioButtonVibrateOn else R.id.radioButtonVibrateOff
                )
                binding.radioGroupSound.check(
                    if (it.soundEnabled) R.id.radioButtonSoundOn else R.id.radioButtonSoundOff
                )
            }
        }

        viewModel.saveStatus.observe(this) { status ->
            Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
        }

        binding.textViewNotificationTone.setOnClickListener {
            openRingtonePicker()
        }

        binding.radioGroupVibrate.setOnCheckedChangeListener { _, checkedId ->
            viewModel.setVibrateEnabled(checkedId == R.id.radioButtonVibrateOn)
        }

        binding.radioGroupSound.setOnCheckedChangeListener { _, checkedId ->
            val soundEnabled = checkedId == R.id.radioButtonSoundOn
            viewModel.setSoundEnabled(soundEnabled)
        }
    }

    private fun openRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Notification Tone")

        val currentToneUriString = viewModel.settings.value?.toneUri
        val currentToneUri = if (!currentToneUriString.isNullOrEmpty() && currentToneUriString != "null") { // Periksa "null" string juga
            try {
                Uri.parse(currentToneUriString)
            } catch (e: Exception) {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) // Fallback jika parse gagal
            }
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentToneUri)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)

        try {
            ringtonePickerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open ringtone picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}