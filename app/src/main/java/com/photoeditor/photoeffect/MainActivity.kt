package com.photoeditor.photoeffect

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.viewpager.widget.PagerAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.photoeditor.photoeffect.SplashActivity.Companion.isFromSplash
import com.photoeditor.photoeffect.databinding.ActivityMainBinding
import com.vorlonsoft.android.rate.AppRate
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var binding: ActivityMainBinding

    // Control double-clicks
    private var mLastClickTime: Long = 0

    private val PICK_IMAGE: Int = 111
    private val CAMERA_REQUEST: Int = 123

    // Initializing with empty list to prevent "UninitializedPropertyAccessException"
    private var gallary_images: ArrayList<String> = ArrayList()
    private lateinit var adapter: ImageAdapter
    private var timer: Timer? = null
    private var mCapturedImageUri: Uri? = null

    companion object {
        var isFromSaved: Boolean = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(binding.root)

        setupAppRate()

        handleSplashScreen()
        initClickListeners()

        // Initial check: Start the permission flow once on launch
        if (checkPermission()) {
            setAdapter()
        } else {
            requestPermission()
        }
    }

    private fun initClickListeners() {
        binding.btnSelect.setOnClickListener(this)
        binding.btnCollage.setOnClickListener(this)
        binding.btnCamera.setOnClickListener(this)
        binding.imgShare.setOnClickListener(this)
        binding.imgRate.setOnClickListener(this)
        binding.imgCreation.setOnClickListener(this)
        binding.imgBack.setOnClickListener(this)
        binding.imgNext.setOnClickListener(this)
    }

    // Passive check: Refresh images ONLY if user granted permission via Settings
    override fun onResume() {
        super.onResume()
        if (checkPermission()) {
            // Only load if the list is still empty (e.g. returning from settings)
            if (gallary_images.isEmpty()) {
                setAdapter()
            }
        }
    }

    // Debounce logic to prevent multiple rapid clicks
    private fun checkClick(): Boolean {
        if (SystemClock.elapsedRealtime() - mLastClickTime < 1000) return false
        mLastClickTime = SystemClock.elapsedRealtime()
        return true
    }

    override fun onClick(v: View?) {
        if (!checkClick()) return

        // Safety Interceptor: Block feature usage if permissions are missing
        if (!checkPermission()) {
            requestPermission()
            return
        }

        when (v?.id) {
            R.id.btn_select -> {
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                intent.type = "image/*"
                startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE)
            }
            R.id.btn_collage -> {
                startActivity(Intent(this, SelectImageActivity::class.java))
            }
            R.id.btn_camera -> openCamera()
            R.id.img_share -> shareApp()
            R.id.img_rate -> rateApp()
            R.id.img_creation -> startActivity(Intent(this, MyCreationActivity::class.java))
            R.id.img_back -> {
                timer?.cancel()
                if (gallary_images.isNotEmpty()) {
                    val prevItem = if (binding.pagerImages.currentItem <= 0) gallary_images.size - 1 else binding.pagerImages.currentItem - 1
                    binding.pagerImages.setCurrentItem(prevItem, true)
                }
            }
            R.id.img_next -> {
                timer?.cancel()
                if (gallary_images.isNotEmpty()) {
                    binding.pagerImages.setCurrentItem((binding.pagerImages.currentItem + 1) % gallary_images.size, true)
                }
            }
        }
    }

    private fun checkPermission(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestPermission() {
        val permissions = getRequiredPermissions()
        val showRationale = permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }

        if (showRationale) {
            showRationaleDialog(permissions)
        } else {
            ActivityCompat.requestPermissions(this, permissions, 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                setAdapter()
            } else {
                // If denied, check if we can still show the system dialog or if it's permanent
                val stillCanAsk = permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }
                if (stillCanAsk) {
                    showRationaleDialog(permissions as Array<String>)
                } else {
                    showSettingsDialog()
                }
            }
        }
    }

    private fun showRationaleDialog(permissions: Array<String>) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app needs Camera and Storage access to show and edit your photos. Please grant permissions to continue.")
            .setCancelable(false)
            .setPositiveButton("Grant") { _, _ ->
                ActivityCompat.requestPermissions(this, permissions, 100)
            }
            .setNegativeButton("Exit App") { _, _ -> finish() }
            .show()
    }

    private fun showSettingsDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Permissions Disabled")
            .setMessage("You have permanently denied permissions. Please enable them in App Settings to use this app.")
            .setCancelable(false)
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Exit App") { _, _ -> finish() }
            .show()
    }

    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
        }
    }

    fun setAdapter() {
        val paths = ImagesPath()
        if (paths.isEmpty()) return

        gallary_images = paths
        adapter = ImageAdapter()
        binding.pagerImages.adapter = adapter
        startAutoScroll()
    }

    // ... (rest of the helper methods: openCamera, shareApp, rateApp, ImagesPath remain same)

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            val photofile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "IMG_${System.currentTimeMillis()}.jpg")
            mCapturedImageUri = FileProvider.getUriForFile(this, "$packageName.provider", photofile)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, mCapturedImageUri)
            startActivityForResult(intent, CAMERA_REQUEST)
        } catch (e: Exception) {
            Log.e("CameraError", e.message ?: "Error creating file")
        }
    }

    private fun shareApp() {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            putExtra(Intent.EXTRA_TEXT, getString(R.string.txt_share))
        }
        startActivity(Intent.createChooser(i, "Share App Via"))
    }

    private fun rateApp() {
        val uri = Uri.parse("market://details?id=$packageName")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=$packageName")))
        }
    }

    private fun startAutoScroll() {
        timer?.cancel()
        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                binding.pagerImages.post {
                    if (gallary_images.isNotEmpty()) {
                        binding.pagerImages.setCurrentItem((binding.pagerImages.currentItem + 1) % gallary_images.size, true)
                    }
                }
            }
        }, 4000, 4000)
    }

    private fun handleSplashScreen() {
        if (isFromSplash) {
            window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            Handler(Looper.getMainLooper()).postDelayed({
                isFromSplash = false
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            }, 1000)
        }
    }

    private fun setupAppRate() {
        AppRate.with(this).setInstallDays(2.toByte()).setLaunchTimes(2.toByte()).monitor()
    }

    fun ImagesPath(): ArrayList<String> {
        val listOfAllImages = ArrayList<String>()
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        val cursor = contentResolver.query(uri, projection, null, null, "${MediaStore.Images.Media.DATE_TAKEN} DESC")

        cursor?.use {
            val columnIndexData = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            while (it.moveToNext()) {
                listOfAllImages.add(it.getString(columnIndexData))
            }
        }
        return listOfAllImages
    }

    inner class ImageAdapter : PagerAdapter() {
        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val view = LayoutInflater.from(this@MainActivity).inflate(R.layout.item_slider, container, false)
            val imgSlider: ImageView = view.findViewById(R.id.img_slider)

            Glide.with(this@MainActivity)
                .load(gallary_images[position])
                .apply(RequestOptions.circleCropTransform())
                .into(imgSlider)

            container.addView(view)

            imgSlider.setOnClickListener {
                if (!checkClick()) return@setOnClickListener
                val intent = Intent(this@MainActivity, ImageEditActivity::class.java)
                intent.putExtra("image_uri", Uri.fromFile(File(gallary_images[position])).toString())
                startActivity(intent)
            }
            return view
        }
        override fun isViewFromObject(v: View, obj: Any): Boolean = v == obj
        override fun getCount(): Int = gallary_images.size
        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) = container.removeView(obj as View)
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}