// SoundAdapter.kt
package com.example.linkup.ui.soundboards

import android.content.Context
import android.graphics.Color // Bisa dihapus jika tidak ada parsing warna lagi
import android.graphics.drawable.ColorDrawable // Bisa dihapus jika tidak ada parsing warna lagi
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.linkup.R
import com.example.linkup.databinding.ItemSoundBinding
import com.example.linkup.model.SoundItem

class SoundAdapter(
    private val onPlayClicked: (SoundItem, Int) -> Unit,
    private val onDeleteClicked: (SoundItem) -> Unit
) : ListAdapter<SoundItem, SoundAdapter.SoundViewHolder>(SoundDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SoundViewHolder {
        val binding = ItemSoundBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SoundViewHolder(binding, parent.context)
    }

    override fun onBindViewHolder(holder: SoundViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class SoundViewHolder(
        private val binding: ItemSoundBinding,
        private val context: Context
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(soundItem: SoundItem) {
            binding.soundTitle.text = soundItem.title

            // Karena soundItem TIDAK memiliki iconName atau backgroundColor,
            // kita akan menggunakan tampilan default/statis.

            if (soundItem.isPlayingUi) {
                // Tampilan saat item sedang diputar
                // Contoh:
                // Ganti background item layout
                binding.soundItemLayout.setBackgroundColor(ContextCompat.getColor(context, R.color.sound_item_playing_background)) // Definisikan warna ini di colors.xml
                // Ganti ikon di ImageView
                binding.soundIcon.setImageResource(R.drawable.ic_soundboards) // Buat drawable ini
                // Ganti ikon tombol play/pause
                binding.buttonPlay.setImageResource(R.drawable.ic_pause) // Anda sudah punya ini
            } else {
                // Tampilan saat item tidak diputar (default)
                // Contoh:
                // Kembalikan background item layout ke default
                binding.soundItemLayout.setBackgroundColor(ContextCompat.getColor(context, R.color.sound_item_default_background)) // Definisikan warna ini di colors.xml
                // Kembalikan ikon di ImageView ke default
                binding.soundIcon.setImageResource(R.drawable.ic_soundboards) // Buat drawable ini
                // Kembalikan ikon tombol play/pause
                binding.buttonPlay.setImageResource(R.drawable.ic_play) // Anda sudah punya ini
            }

            binding.buttonPlay.setOnClickListener {
                onPlayClicked(soundItem, adapterPosition)
            }

            binding.buttonDelete.setOnClickListener {
                onDeleteClicked(soundItem)
            }
        }
    }

    class SoundDiffCallback : DiffUtil.ItemCallback<SoundItem>() {
        override fun areItemsTheSame(oldItem: SoundItem, newItem: SoundItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SoundItem, newItem: SoundItem): Boolean {
            // Karena isPlayingUi adalah bagian dari state yang ingin direfleksikan perubahannya
            // dan merupakan bagian dari data class yang dibandingkan, ini sudah benar.
            return oldItem == newItem
        }
    }
}