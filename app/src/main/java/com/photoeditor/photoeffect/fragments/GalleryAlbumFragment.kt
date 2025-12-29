package com.photoeditor.photoeffect.fragments

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.provider.MediaStore
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import com.photoeditor.photoeffect.R
import com.photoeditor.photoeffect.adapter.GalleryAlbumAdapter
import com.photoeditor.photoeffect.adapter.GalleryAlbumRecyclerAdapter
import com.photoeditor.photoeffect.databinding.FragmentGalleryAlbumBinding
import com.photoeditor.photoeffect.model.GalleryAlbum
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class GalleryAlbumFragment : Fragment() {

    // View Binding property
    private var _binding: FragmentGalleryAlbumBinding? = null
    private val binding get() = _binding!!

    private lateinit var mAlbums: ArrayList<GalleryAlbum>
    private lateinit var mAdapter: GalleryAlbumRecyclerAdapter

    // Important: Fragments should not have constructors with parameters.
    // Use requireContext() inside the fragment instead.

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate layout using View Binding
        _binding = FragmentGalleryAlbumBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Execute data loading
        LoadAlbumsTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    private inner class LoadAlbumsTask : AsyncTask<Void, Void, ArrayList<GalleryAlbum>>() {

        override fun onPreExecute() {
            super.onPreExecute()
            binding.progressBar.visibility = View.VISIBLE
        }

        override fun doInBackground(vararg params: Void?): ArrayList<GalleryAlbum> {
            return loadPhotoAlbums(requireContext())
        }

        override fun onPostExecute(result: ArrayList<GalleryAlbum>?) {
            super.onPostExecute(result)

            // Check if binding is still valid (fragment might be detached)
            if (_binding == null) return

            binding.progressBar.visibility = View.GONE
            mAlbums = result ?: ArrayList()

            mAdapter = GalleryAlbumRecyclerAdapter(
                requireContext(),
                mAlbums,
                object : GalleryAlbumAdapter.OnGalleryAlbumClickListener {
                    override fun onGalleryAlbumClick(galleryAlbum: GalleryAlbum?) {
                        if (galleryAlbum == null) return

                        val bundle = Bundle().apply {
                            putStringArrayList(
                                GalleryAlbumImageFragment.ALBUM_IMAGE_EXTRA,
                                galleryAlbum.mImageList as java.util.ArrayList<String>
                            )
                            putString(
                                GalleryAlbumImageFragment.ALBUM_NAME_EXTRA,
                                galleryAlbum.mAlbumName
                            )
                        }

                        val galleryalbumImageFragment = GalleryAlbumImageFragment().apply {
                            arguments = bundle
                        }

                        // FIXED: Using requireActivity().supportFragmentManager
                        requireActivity().supportFragmentManager.beginTransaction()
                            .replace(R.id.frame_container, galleryalbumImageFragment)
                            .addToBackStack(null)
                            .commit()
                    }
                })

            binding.listView.apply {
                layoutManager = GridLayoutManager(requireContext(), 3)
                adapter = mAdapter
            }
        }
    }

    private fun loadPhotoAlbums(context: Context): ArrayList<GalleryAlbum> {
        val albumMap = LinkedHashMap<Long, GalleryAlbum>()
        val projection = arrayOf("_id", "_data", "bucket_id", "bucket_display_name", "datetaken")
        val uri: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val cursor: Cursor? = context.contentResolver.query(
            uri, projection, null, null, "date_added DESC"
        )

        val arrayList = ArrayList<GalleryAlbum>()

        cursor?.use {
            val nameIndex = it.getColumnIndexOrThrow("bucket_display_name")
            val dateIndex = it.getColumnIndexOrThrow("datetaken")
            val dataIndex = it.getColumnIndexOrThrow("_data")
            val idIndex = it.getColumnIndexOrThrow("bucket_id")

            while (it.moveToNext()) {
                val name = it.getString(nameIndex) ?: "Unknown"
                val dateTaken = it.getLong(dateIndex)
                val path = it.getString(dataIndex)
                val bucketId = it.getLong(idIndex)

                var album = albumMap[bucketId]
                if (album == null) {
                    album = GalleryAlbum(bucketId, name)
                    album.mTakenDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(dateTaken)
                    album.mImageList.add(path)
                    albumMap[bucketId] = album
                } else {
                    album.mImageList.add(path)
                }
            }
            arrayList.addAll(albumMap.values)
        }
        return arrayList
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up binding to avoid memory leaks
        _binding = null
    }
}