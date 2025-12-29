package com.photoeditor.photoeffect

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.photoeditor.photoeffect.adapter.SelectedPhotoAdapter
import com.photoeditor.photoeffect.databinding.ActivitySelectImageBinding
import com.photoeditor.photoeffect.fragments.GalleryAlbumFragment
import com.photoeditor.photoeffect.fragments.GalleryAlbumImageFragment
import java.util.ArrayList

class SelectImageActivity : AppCompatActivity(),
    GalleryAlbumImageFragment.OnSelectImageListener,
    SelectedPhotoAdapter.OnDeleteButtonClickListener {

    // 1. Declare the binding variable
    private lateinit var binding: ActivitySelectImageBinding

    private val mSelectedImages = ArrayList<String>()
    private var maxImageCount = 10
    private lateinit var mSelectedPhotoAdapter: SelectedPhotoAdapter
    private var mLastClickTime: Long = 0

    // Improved checkClick to return a Boolean for easier use in listeners
    private fun isClickValid(): Boolean {
        if (SystemClock.elapsedRealtime() - mLastClickTime < 1000) {
            return false
        }
        mLastClickTime = SystemClock.elapsedRealtime()
        return true
    }

    /**
     * SelectedPhotoAdapter.OnDeleteButtonClickListener implementation
     */
    override fun onDeleteButtonClick(str: String) {
        mSelectedImages.remove(str)
        updatePhotoCountUI()
    }

    /**
     * GalleryAlbumImageFragment.OnSelectImageListener implementation
     * Note: str is non-nullable to match standard interface signatures
     */
    override fun onSelectImage(str: String) {
        if (mSelectedImages.size >= maxImageCount) {
            Toast.makeText(
                this,
                "You only need $maxImageCount photo(s)",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            mSelectedImages.add(str)
            updatePhotoCountUI()
        }
    }

    // Centralized function to update RecyclerView and the count TextView via binding
    private fun updatePhotoCountUI() {
        mSelectedPhotoAdapter.notifyDataSetChanged()
        val countText = "Select upto 10 photo(s) (${mSelectedImages.size})"
        binding.textImgcount.text = countText
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. Initialize View Binding
        binding = ActivitySelectImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mSelectedPhotoAdapter = SelectedPhotoAdapter(mSelectedImages, this)

        // 3. Access views via binding (Safe and type-secure)
        binding.listImages.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@SelectImageActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = mSelectedPhotoAdapter
        }

        // Load the gallery fragment into the container
        // Note: FrameLayout ID 'frame_container' is typically used for transactions
        supportFragmentManager.beginTransaction()
            .replace(R.id.frame_container, GalleryAlbumFragment())
            .commit()

        // Setup Next Button Click
        binding.btnNext.setOnClickListener {
            if (isClickValid()) {
                createCollage()
            }
        }
    }

    private fun createCollage() {
        if (mSelectedImages.isEmpty()) {
            Toast.makeText(this, "Please select photo(s)", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intent = Intent(this, CollageActivity::class.java)
            // Passing selection count and the ArrayList of paths
            intent.putExtra("imageCount", mSelectedImages.size)
            intent.putStringArrayListExtra("selectedImages", mSelectedImages)
            intent.putExtra("imagesinTemplate", mSelectedImages.size)

            startActivityForResult(intent, 111)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error starting collage", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK) return

        if (requestCode == 111) {
            // Success: Return to main menu and clear stack
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }
}