package com.photoeditor.photoeffect

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.photoeditor.photoeffect.databinding.ActivityShowImageBinding
import java.io.File

class ShowImageActivity : AppCompatActivity(), View.OnClickListener {

    // 1. Initialize Binding
    private lateinit var binding: ActivityShowImageBinding

    private var image_uri: String? = null
    private var saved_file: File? = null
    private var density: Float = 0.toFloat()
    internal var D_height: Int = 0
    internal var D_width: Int = 0
    private var display: DisplayMetrics? = null

    private var mLastClickTime: Long = 0

    // Improved click check logic
    private fun checkClick(): Boolean {
        if (SystemClock.elapsedRealtime() - mLastClickTime < 1000) {
            return false
        }
        mLastClickTime = SystemClock.elapsedRealtime()
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen Setup
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // 2. Initialize View Binding
        binding = ActivityShowImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        image_uri = intent.getStringExtra("image_uri")

        if (image_uri != null) {
            saved_file = File(image_uri!!)

            // Layout Calculations
            display = resources.displayMetrics
            density = resources.displayMetrics.density
            D_width = display!!.widthPixels
            D_height = (display!!.heightPixels.toFloat() - density * 150.0f).toInt()

            val layoutParams = RelativeLayout.LayoutParams(D_width, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE)

            // 3. Access views via binding
            binding.imgShow.layoutParams = layoutParams
            binding.imgShow.setImageURI(Uri.parse(image_uri))
        }

        // 4. Set Click Listeners using binding
        binding.whatsappShare.setOnClickListener(this)
        binding.facebookShare.setOnClickListener(this)
        binding.instagramShare.setOnClickListener(this)
        binding.messangerShare.setOnClickListener(this)
        binding.twitterShare.setOnClickListener(this)
        binding.shareMore.setOnClickListener(this)
        binding.imgFolder.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        if (!checkClick()) return // Stop multiple rapid clicks

        when (v.id) {
            R.id.whatsapp_share -> shareImageSocialApp("com.whatsapp", "Whatsapp")

            R.id.instagram_share -> shareImageSocialApp("com.instagram.android", "Instagram")

            R.id.twitter_share -> shareImageSocialApp("com.twitter.android", "Twitter")

            R.id.messanger_share -> shareImageSocialApp("com.facebook.orca", "Facebook Messenger")

            R.id.facebook_share -> shareImageSocialApp("com.facebook.katana", "Facebook")

            R.id.share_more -> {
                val share = Intent(Intent.ACTION_SEND)
                share.type = "image/*"
                val uri = FileProvider.getUriForFile(
                    this,
                    "$packageName.provider",
                    saved_file!!
                )
                share.putExtra(Intent.EXTRA_STREAM, uri)
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(share, "Share Image"))
            }

            R.id.img_folder -> {
                val intent = Intent(this, MyCreationActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }

    fun shareImageSocialApp(pkg: String, appName: String) {
        val share = Intent(Intent.ACTION_SEND)
        share.type = "image/*"

        try {
            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.provider",
                saved_file!!
            )
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            share.putExtra(Intent.EXTRA_STREAM, uri)
            share.putExtra(Intent.EXTRA_TEXT, getString(R.string.txt_share))

            if (isPackageInstalled(pkg, this)) {
                share.setPackage(pkg)
                startActivity(share) // Choice not needed if package is forced
                return
            }
            Toast.makeText(this, "Please Install $appName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Sharing failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isPackageInstalled(packagename: String, context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(packagename, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    override fun onBackPressed() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}