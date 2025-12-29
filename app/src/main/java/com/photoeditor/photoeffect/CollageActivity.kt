package com.photoeditor.photoeffect

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.RelativeLayout
import android.widget.SeekBar
import androidx.recyclerview.widget.LinearLayoutManager
import com.photoeditor.photoeffect.adapter.BackgroundAdapter
import com.photoeditor.photoeffect.adapter.FrameAdapter
import com.photoeditor.photoeffect.frame.FramePhotoLayout
import com.photoeditor.photoeffect.multitouch.PhotoView
import com.photoeditor.photoeffect.model.TemplateItem
import com.photoeditor.photoeffect.utils.FrameImageUtils
import com.photoeditor.photoeffect.utils.ImageUtils
import com.photoeditor.photoeffect.databinding.ActivityCollageBinding // Added Binding
import android.content.Intent
import android.os.SystemClock
import android.widget.ImageView
import java.io.*

class CollageActivity : AppCompatActivity(), View.OnClickListener,
    FrameAdapter.OnFrameClickListener, BackgroundAdapter.OnBGClickListener {

    // 1. Initialize Binding
    private lateinit var binding: ActivityCollageBinding

    var mFramePhotoLayout: FramePhotoLayout? = null
    var DEFAULT_SPACE: Float = 0.0f
    var MAX_SPACE: Float = 0.0f
    var MAX_CORNER: Float = 0.0f

    protected val RATIO_SQUARE = 0
    protected val RATIO_GOLDEN = 2

    private var mSpace = 0f
    private var mCorner = 0f
    val MAX_SPACE_PROGRESS = 300.0f
    val MAX_CORNER_PROGRESS = 200.0f
    private var mBackgroundColor = Color.WHITE
    private var mBackgroundImage: Bitmap? = null
    private var mBackgroundUri: Uri? = null
    private var mSavedInstanceState: Bundle? = null
    protected var mLayoutRatio = RATIO_SQUARE
    protected lateinit var mPhotoView: PhotoView
    protected var mOutputScale = 1f
    protected var mSelectedTemplateItem: TemplateItem? = null
    private var mImageInTemplateCount = 0
    protected var mTemplateItemList: ArrayList<TemplateItem>? = ArrayList()
    protected var mSelectedPhotoPaths: MutableList<String> = java.util.ArrayList()

    lateinit var frameAdapter: FrameAdapter

    private var mLastClickTime: Long = 0
    fun checkClick() {
        if (SystemClock.elapsedRealtime() - mLastClickTime < 1000) {
            return
        }
        mLastClickTime = SystemClock.elapsedRealtime()
    }

    override fun onBGClick(drawable: Drawable) {
        mFramePhotoLayout?.let {
            val bmp = it.createImage()
            val bitmap = (drawable as BitmapDrawable).bitmap
            mBackgroundImage = AndroidUtils.resizeImageToNewSize(bitmap, bmp.width, bmp.height)
            binding.imgBackground.setImageBitmap(mBackgroundImage)
        }
    }

    override fun onFrameClick(templateItem: TemplateItem) {
        mSelectedTemplateItem?.isSelected = false

        mSelectedTemplateItem?.photoItemList?.forEachIndexed { idx, photoItem ->
            if (!photoItem.imagePath.isNullOrEmpty()) {
                if (idx < mSelectedPhotoPaths.size) {
                    mSelectedPhotoPaths[idx] = photoItem.imagePath!!
                } else {
                    mSelectedPhotoPaths.add(photoItem.imagePath!!)
                }
            }
        }

        val size = Math.min(mSelectedPhotoPaths.size, templateItem.photoItemList.size)
        for (idx in 0 until size) {
            val photoItem = templateItem.photoItemList[idx]
            if (photoItem.imagePath.isNullOrEmpty()) {
                photoItem.imagePath = mSelectedPhotoPaths[idx]
            }
        }

        mSelectedTemplateItem = templateItem
        mSelectedTemplateItem!!.isSelected = true
        frameAdapter.notifyDataSetChanged()
        buildLayout(templateItem)
    }

    inner class space_listener : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            mSpace = MAX_SPACE * (seekBar?.progress ?: 0) / MAX_SPACE_PROGRESS
            mFramePhotoLayout?.setSpace(mSpace, mCorner)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    inner class corner_listener : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            mCorner = MAX_CORNER * (seekBar?.progress ?: 0) / MAX_CORNER_PROGRESS
            mFramePhotoLayout?.setSpace(mSpace, mCorner)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    override fun onClick(v: View?) {
        val accentColor = resources.getColor(R.color.colorAccent)
        val windowBg = resources.getColor(R.color.windowBackground)

        when (v?.id) {
            R.id.tab_layout -> {
                updateTabs(accentColor, windowBg, windowBg)
                binding.llFrame.visibility = View.VISIBLE
                binding.llBorder.visibility = View.GONE
                binding.llBg.visibility = View.GONE
            }
            R.id.tab_border -> {
                updateTabs(windowBg, accentColor, windowBg)
                binding.llFrame.visibility = View.GONE
                binding.llBorder.visibility = View.VISIBLE
                binding.llBg.visibility = View.GONE
            }
            R.id.tab_bg -> {
                updateTabs(windowBg, windowBg, accentColor)
                binding.llFrame.visibility = View.GONE
                binding.llBorder.visibility = View.GONE
                binding.llBg.visibility = View.VISIBLE
            }
            R.id.btn_next -> {
                checkClick()
                try {
                    val collageBitmap = createOutputImage()
                    val file = File(cacheDir, "tempBMP")
                    FileOutputStream(file).use { out ->
                        collageBitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                startActivity(Intent(this, FilterCollageActivity::class.java))
                finish()
            }
        }
    }

    private fun updateTabs(layoutCol: Int, borderCol: Int, bgCol: Int) {
        binding.tabLayout.setBackgroundColor(layoutCol)
        binding.tabBorder.setBackgroundColor(borderCol)
        binding.tabBg.setBackgroundColor(bgCol)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize View Binding
        binding = ActivityCollageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        DEFAULT_SPACE = ImageUtils.pxFromDp(this, 2F)
        MAX_SPACE = ImageUtils.pxFromDp(this, 30F)
        MAX_CORNER = ImageUtils.pxFromDp(this, 60F)
        mSpace = DEFAULT_SPACE

        savedInstanceState?.let {
            mSpace = it.getFloat("mSpace")
            mCorner = it.getFloat("mCorner")
            mSavedInstanceState = it
        }

        mImageInTemplateCount = intent.getIntExtra("imagesinTemplate", 0)
        val extraImagePaths = intent.getStringArrayListExtra("selectedImages")

        binding.listBg.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.listBg.adapter = BackgroundAdapter(this, this)

        binding.tabLayout.setOnClickListener(this)
        binding.tabBorder.setOnClickListener(this)
        binding.tabBg.setOnClickListener(this)
        binding.btnNext.setOnClickListener(this)

        binding.seekbarSpace.setOnSeekBarChangeListener(space_listener())
        binding.seekbarCorner.setOnSeekBarChangeListener(corner_listener())

        mPhotoView = PhotoView(this)

        binding.rlContainer.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                mOutputScale = ImageUtils.calculateOutputScaleFactor(
                    binding.rlContainer.width,
                    binding.rlContainer.height
                )
                mSelectedTemplateItem?.let { buildLayout(it) }
                binding.rlContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

        loadFrameImages()
        binding.listFrames.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        frameAdapter = FrameAdapter(this, mTemplateItemList!!, this)
        binding.listFrames.adapter = frameAdapter

        if (!mTemplateItemList.isNullOrEmpty()) {
            mSelectedTemplateItem = mTemplateItemList!![0].apply { isSelected = true }
        }

        extraImagePaths?.let { paths ->
            val size = Math.min(paths.size, mSelectedTemplateItem?.photoItemList?.size ?: 0)
            for (i in 0 until size) {
                mSelectedTemplateItem?.photoItemList?.get(i)?.imagePath = paths[i]
            }
        }
    }

    private fun loadFrameImages() {
        val allItems = FrameImageUtils.loadFrameImages(this)
        mTemplateItemList = if (mImageInTemplateCount > 0) {
            ArrayList(allItems.filter { it.photoItemList.size == mImageInTemplateCount })
        } else {
            ArrayList(allItems)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putFloat("mSpace", mSpace)
        outState.putFloat("mCornerBar", mCorner)
        mFramePhotoLayout?.saveInstanceState(outState)
    }

    fun buildLayout(item: TemplateItem) {
        mFramePhotoLayout = FramePhotoLayout(this, item.photoItemList)

        var viewWidth = binding.rlContainer.width
        var viewHeight = binding.rlContainer.height

        if (mLayoutRatio == RATIO_SQUARE) {
            val minSide = Math.min(viewWidth, viewHeight)
            viewWidth = minSide
            viewHeight = minSide
        } else if (mLayoutRatio == RATIO_GOLDEN) {
            val goldenRatio = 1.61803398875
            if (viewWidth <= viewHeight) {
                viewHeight = Math.min(viewHeight, (viewWidth * goldenRatio).toInt())
                viewWidth = (viewHeight / goldenRatio).toInt()
            } else {
                viewWidth = Math.min(viewWidth, (viewHeight * goldenRatio).toInt())
                viewHeight = (viewWidth / goldenRatio).toInt()
            }
        }

        mOutputScale = ImageUtils.calculateOutputScaleFactor(viewWidth, viewHeight)
        mFramePhotoLayout!!.build(viewWidth, viewHeight, mOutputScale, mSpace, mCorner)

        mSavedInstanceState?.let {
            mFramePhotoLayout!!.restoreInstanceState(it)
            mSavedInstanceState = null
        }

        val params = RelativeLayout.LayoutParams(viewWidth, viewHeight).apply {
            addRule(RelativeLayout.CENTER_IN_PARENT)
        }

        binding.rlContainer.removeAllViews()

        // Re-add views in correct order
        addManagedView(binding.imgBackground, params)
        addManagedView(mFramePhotoLayout!!, params)
        addManagedView(mPhotoView, params)

        binding.seekbarSpace.progress = (MAX_SPACE_PROGRESS * mSpace / MAX_SPACE).toInt()
        binding.seekbarCorner.progress = (MAX_CORNER_PROGRESS * mCorner / MAX_CORNER).toInt()
    }

    private fun addManagedView(view: View, params: RelativeLayout.LayoutParams) {
        (view.parent as? ViewGroup)?.removeView(view)
        binding.rlContainer.addView(view, params)
    }

    @Throws(OutOfMemoryError::class)
    fun createOutputImage(): Bitmap {
        val template = mFramePhotoLayout!!.createImage()
        val result = Bitmap.createBitmap(template.width, template.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (mBackgroundImage != null && !mBackgroundImage!!.isRecycled) {
            canvas.drawBitmap(mBackgroundImage!!, null, Rect(0, 0, result.width, result.height), paint)
        } else {
            canvas.drawColor(mBackgroundColor)
        }

        canvas.drawBitmap(template, 0f, 0f, paint)
        template.recycle()

        mPhotoView.getImage(mOutputScale)?.let { stickers ->
            canvas.drawBitmap(stickers, 0f, 0f, paint)
            stickers.recycle()
        }

        System.gc()
        return result
    }
}