package com.photoeditor.photoeffect.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.photoeditor.photoeffect.adapter.GalleryAlbumImageAdapter
import com.photoeditor.photoeffect.databinding.FragmentGalleryAlbumImageBinding

class GalleryAlbumImageFragment : Fragment() {

    // 1. Initialize Binding with Fragment lifecycle safety
    private var _binding: FragmentGalleryAlbumImageBinding? = null
    private val binding get() = _binding!!

    private var mImages: ArrayList<String> = ArrayList()
    private var mAlbumName: String? = null
    private var mListener: OnSelectImageListener? = null

    // Interface for Activity communication
    interface OnSelectImageListener {
        fun onSelectImage(str: String)
    }

    companion object {
        const val ALBUM_IMAGE_EXTRA = "albumImage"
        const val ALBUM_NAME_EXTRA = "albumName"
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // 2. Safely cast the listener from the Activity
        if (context is OnSelectImageListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnSelectImageListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 3. Extract arguments using the safe call let block
        arguments?.let {
            mImages = it.getStringArrayList(ALBUM_IMAGE_EXTRA) ?: ArrayList()
            mAlbumName = it.getString(ALBUM_NAME_EXTRA)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 4. Inflate layout using View Binding
        _binding = FragmentGalleryAlbumImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 5. Setup GridView/RecyclerView logic via binding
        if (mImages.isNotEmpty()) {
            val adapter = GalleryAlbumImageAdapter(requireContext(), mImages)

            // If your XML still uses <GridView>:
            binding.gridView.adapter = adapter
            binding.gridView.setOnItemClickListener { _, _, position, _ ->
                mListener?.onSelectImage(mImages[position])
            }

            /* Note: If you transition to RecyclerView, the logic moves
               inside the Adapter's onBindViewHolder.
            */
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 6. Nullify binding to avoid memory leaks
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }
}