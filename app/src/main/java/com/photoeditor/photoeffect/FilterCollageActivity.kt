package com.photoeditor.photoeffect

import android.content.ContentValues
import android.content.Intent
import android.graphics.*
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.photoeditor.photoeffect.MainActivity.Companion.isFromSaved
import com.photoeditor.photoeffect.adapter.FilterNameAdapter
import com.photoeditor.photoeffect.databinding.ActivityFilterCollageBinding // Binding Import
import com.photoeditor.photoeffect.model.FilterData
import java.io.File
import java.io.FileOutputStream
import java.util.*

class FilterCollageActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var binding: ActivityFilterCollageBinding
    private var mLastClickTime: Long = 0
    private var savedImageUri: Uri? = null
    lateinit var bmp: Bitmap

    companion object {
        var red: Float = 0F
        var green: Float = 0F
        var blue: Float = 0F
        var saturation: Float = 0F
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFilterCollageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load bitmap from cache
        val bitmapPath = File(cacheDir, "tempBMP").absolutePath
        bmp = BitmapFactory.decodeFile(bitmapPath)

        binding.imgCollage.setImageBitmap(bmp)
        binding.imgSave.setOnClickListener(this)

        // Setup Filter Type List
        binding.listFilterstype.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        var filterTypeAdapter = FilterDetailAdapter(AndroidUtils.filter_clr1)
        binding.listFilterstype.adapter = filterTypeAdapter

        // Setup Filter Names List
        binding.filterNames.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        val filterNameAdapter = FilterNameAdapter(this, resources.getStringArray(R.array.filters))

        filterNameAdapter.setOnFilterNameClick(object : FilterNameAdapter.FilterNameClickListener {
            override fun onItemClick(view: View, position: Int) {
                val filterDataArray = when (position) {
                    0 -> AndroidUtils.filter_clr1
                    1 -> AndroidUtils.filter_clr2
                    2 -> AndroidUtils.filter_duo
                    3 -> AndroidUtils.filter_pink
                    4 -> AndroidUtils.filter_fresh
                    5 -> AndroidUtils.filter_euro
                    6 -> AndroidUtils.filter_dark
                    7 -> AndroidUtils.filter_ins
                    8 -> AndroidUtils.filter_elegant
                    9 -> AndroidUtils.filter_golden
                    10 -> AndroidUtils.filter_tint
                    11 -> AndroidUtils.filter_film
                    12 -> AndroidUtils.filter_lomo
                    13 -> AndroidUtils.filter_movie
                    14 -> AndroidUtils.filter_retro
                    15 -> AndroidUtils.filter_bw
                    else -> AndroidUtils.filter_clr1
                }
                filterTypeAdapter = FilterDetailAdapter(filterDataArray)
                binding.listFilterstype.adapter = filterTypeAdapter

                filterNameAdapter.notifyDataSetChanged()
            }
        })
        binding.filterNames.adapter = filterNameAdapter
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.img_save -> {
                checkClick()
                isFromSaved = true

                // 1. Capture the screenshot
                val bitmapToSave = getScreenShot(binding.imgCollage)

                // 2. Save and get the absolute path
                val savedPath = saveBitmapAndGetPath(bitmapToSave)

                if (savedPath != null) {
                    // 3. Pass the PATH directly to ensure it exists for the next activity
                    val intent = Intent(this, ShowImageActivity::class.java)
                    intent.putExtra("image_uri", savedPath)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun saveBitmapAndGetPath(bitmap: Bitmap): String? {
        val fileName = "FilterCollage_${System.currentTimeMillis() / 1000}.png"
        val resolver = contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            // Saves to the public "Pictures/ArtisticEditor" folder
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ArtisticEditor")
        }

        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        return try {
            imageUri?.let { uri ->
                resolver.openOutputStream(uri).use { outputStream ->
                    if (outputStream != null) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        // The path returned here is a content:// URI string
                        uri.toString()
                    } else null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    // Modern Screenshot logic without DrawingCache
    private fun getScreenShot(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    fun saveBitmap(bitmap: Bitmap) {
        val mainDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ArtisticEditor")
        if (!mainDir.exists()) mainDir.mkdirs()

        val fileName = "${System.currentTimeMillis() / 1000}.png"
        val file = File(mainDir, fileName)

        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            savedImageUri = Uri.fromFile(file)

            MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null) { path, uri ->
                Log.i("ExternalStorage", "Scanned $path: -> uri=$uri")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkClick() {
        if (SystemClock.elapsedRealtime() - mLastClickTime < 1000) return
        mLastClickTime = SystemClock.elapsedRealtime()
    }

    inner class FilterDetailAdapter(private val filterType: Array<FilterData>) :
        RecyclerView.Adapter<FilterDetailAdapter.FilterDetailHolder>() {

        var selectedIndex = 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterDetailHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_filter, parent, false)
            return FilterDetailHolder(view)
        }

        override fun getItemCount() = filterType.size

        override fun onBindViewHolder(holder: FilterDetailHolder, position: Int) {
            val currentFilter = filterType[position]

            holder.rlFilterItem.setBackgroundColor(
                if (selectedIndex == position) resources.getColor(R.color.colorAccent)
                else resources.getColor(R.color.transparent)
            )

            // Calculate preview thumbnail
            val previewBitmap = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(previewBitmap)
            val paint = Paint().apply {
                val matrix = ColorMatrix().apply {
                    setSaturation(currentFilter.saturation)
                    val scale = ColorMatrix().apply {
                        setScale(currentFilter.red, currentFilter.green, currentFilter.blue, 1F)
                    }
                    postConcat(scale)
                }
                colorFilter = ColorMatrixColorFilter(matrix)
            }
            canvas.drawBitmap(bmp, 0F, 0F, paint)
            holder.thumbnailFilter.setImageBitmap(previewBitmap)
            holder.filterName.text = currentFilter.text

            holder.rlFilterItem.setOnClickListener {
                selectedIndex = position
                red = currentFilter.red
                green = currentFilter.green
                blue = currentFilter.blue
                saturation = currentFilter.saturation

                AsyncFilter(bmp, binding.imgCollage).executeOnExecutor(
                    AsyncTask.THREAD_POOL_EXECUTOR, red, green, blue
                )
                notifyDataSetChanged()
            }
        }

        inner class FilterDetailHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val thumbnailFilter: ImageView = itemView.findViewById(R.id.thumbnail_filter)
            val filterName: TextView = itemView.findViewById(R.id.filterName)
            val rlFilterItem: RelativeLayout = itemView.findViewById(R.id.rl_filteritem)
        }
    }

    // Keep AsyncTask for now but use static params
    class AsyncFilter(private val originalBitmap: Bitmap, private val imgMain: ImageView) :
        AsyncTask<Float, Void, Bitmap>() {

        override fun doInBackground(vararg params: Float?): Bitmap {
            val r = params[0] ?: 1f
            val g = params[1] ?: 1f
            val b = params[2] ?: 1f

            val bitmap = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint().apply {
                val matrix = ColorMatrix().apply {
                    setSaturation(saturation)
                    postConcat(ColorMatrix().apply { setScale(r, g, b, 1f) })
                }
                colorFilter = ColorMatrixColorFilter(matrix)
            }
            canvas.drawBitmap(originalBitmap, 0F, 0F, paint)
            return bitmap
        }

        override fun onPostExecute(result: Bitmap) {
            imgMain.setImageBitmap(result)
        }
    }
}