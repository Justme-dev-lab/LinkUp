package com.example.linkup // atau package adapter Anda

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.linkup.model.Users // Pastikan model Users Anda benar
import com.example.linkup.R

class UserSearchAdapter(
    private val onItemClick: (Users) -> Unit
) : ListAdapter<Users, UserSearchAdapter.UserViewHolder>(UserDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_search_result, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val profileImageView: ImageView = itemView.findViewById(R.id.profileImageUser)
        private val usernameTextView: TextView = itemView.findViewById(R.id.usernameTextView)
        private val emailTextView: TextView = itemView.findViewById(R.id.emailTextView) // Anda mungkin perlu menambahkan email ke model Users jika ingin ditampilkan

        fun bind(user: Users) {
            usernameTextView.text = user.username
            // Jika Anda ingin menampilkan email, dan email ada di model 'Users'
            // emailTextView.text = user.getEmail() // Asumsi ada method getEmail()
            // Jika tidak, Anda bisa mengambil email dari Firebase Auth jika UID cocok,
            // atau hanya tampilkan username. Untuk sekarang, kita asumsikan username cukup.
            // Jika model Users Anda hanya memiliki 'search' sebagai lowercase username, kita tampilkan username asli.
            emailTextView.visibility = View.GONE // Sembunyikan jika tidak ada data email di model Users

            Glide.with(itemView.context)
                .load(user.profile)
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .into(profileImageView)

            itemView.setOnClickListener {
                onItemClick(user)
            }
        }
    }

    class UserDiffCallback : DiffUtil.ItemCallback<Users>() {
        override fun areItemsTheSame(oldItem: Users, newItem: Users): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: Users, newItem: Users): Boolean {
            // Jika Users adalah data class, perbandingan sederhana cukup.
            // Karena bukan data class, kita bandingkan field yang relevan.
            return oldItem == newItem
        }
    }
}