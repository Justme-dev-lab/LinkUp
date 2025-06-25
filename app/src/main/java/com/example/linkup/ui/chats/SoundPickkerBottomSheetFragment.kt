package com.example.linkup.ui.chats // Atau package yang sesuai

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.linkup.ui.message.MessageChatActivity
import com.example.linkup.databinding.FragmentSoundPickerBottomSheetBinding // Buat layout ini
import com.example.linkup.model.SoundItem
import com.example.linkup.ui.soundboards.SoundAdapter // Kita bisa gunakan adapter yang sama
import com.example.linkup.ui.soundboards.SoundboardsViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth

interface SoundSelectionListener {
    fun onSoundSelected(soundItem: SoundItem)
}

class SoundPickerBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentSoundPickerBottomSheetBinding? = null
    private val binding get() = _binding!!

    // Gunakan ViewModel yang sama dengan SoundboardsFragment
    // Jika ViewModel butuh Application context, gunakan AndroidViewModelFactory
    private val soundboardsViewModel: SoundboardsViewModel by lazy {
        ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        ).get(SoundboardsViewModel::class.java)
    }

    private lateinit var soundPickerAdapter: SoundAdapter
    private var soundSelectionListener: SoundSelectionListener? = null

    fun setSoundSelectionListener(listener: MessageChatActivity) {
        this.soundSelectionListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSoundPickerBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (FirebaseAuth.getInstance().currentUser == null) {
            Toast.makeText(context, "You need to be logged in to pick sounds.", Toast.LENGTH_LONG).show()
            dismiss()
            return
        }

        setupRecyclerView()
        setupObservers()

        binding.closeSoundPickerBtn.setOnClickListener {
            dismiss()
        }
    }

    private fun setupRecyclerView() {
        soundPickerAdapter = SoundAdapter(
            onPlayClicked = { soundItem, _ ->
                // Di sini, kita tidak memutar suara, tapi memilihnya
                soundSelectionListener?.onSoundSelected(soundItem)
                dismiss() // Tutup bottom sheet setelah memilih
            },
            onDeleteClicked = {
                // Tombol delete tidak relevan di sini, atau bisa disembunyikan di adapter
                // Atau kita bisa buat versi adapter yang berbeda jika perlu
                Toast.makeText(context, "Cannot delete from here.", Toast.LENGTH_SHORT).show()
            }
        )

        binding.recyclerViewSoundPicker.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = soundPickerAdapter
        }
    }

    private fun setupObservers() {
        soundboardsViewModel.soundItems.observe(viewLifecycleOwner) { items ->
            if (items.isNullOrEmpty()) {
                binding.emptyStateText.visibility = View.VISIBLE
                binding.recyclerViewSoundPicker.visibility = View.GONE
                binding.emptyStateText.text = "No sounds found in your soundboard."
            } else {
                binding.emptyStateText.visibility = View.GONE
                binding.recyclerViewSoundPicker.visibility = View.VISIBLE
                soundPickerAdapter.submitList(items.toList())
            }
            Log.d(TAG, "Sound items for picker: ${items?.size ?: 0}")
        }

        soundboardsViewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                // Hindari menampilkan toast dari ViewModel di sini jika tidak relevan untuk picker
                // Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                // soundboardsViewModel.consumeToastMessage()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Hentikan suara jika ada yang dimainkan oleh ViewModel saat bottom sheet ditutup
        // Ini mungkin tidak diperlukan jika onPlayClicked di adapter ini tidak memicu play
        // soundboardsViewModel.stopAnyPlayingSound()
        binding.recyclerViewSoundPicker.adapter = null // Membersihkan referensi adapter
        _binding = null
    }

    companion object {
        const val TAG = "SoundPickerBottomSheet"
        fun newInstance(): SoundPickerBottomSheetFragment {
            return SoundPickerBottomSheetFragment()
        }
    }
}