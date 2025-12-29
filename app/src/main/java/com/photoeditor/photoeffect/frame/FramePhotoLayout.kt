package com.photoeditor.photoeffect.frame

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.view.DragEvent
import android.view.View
import android.widget.RelativeLayout
import com.photoeditor.photoeffect.template.PhotoItem
import com.photoeditor.photoeffect.utils.ImageDecoder
import com.photoeditor.photoeffect.utils.ImageUtils
import java.util.ArrayList

/**
 * FramePhotoLayout: Manages the collection of FrameImageViews for photo collage templates.
 */
class FramePhotoLayout(context: Context, private val mPhotoItems: List<PhotoItem>) :
    RelativeLayout(context), FrameImageView.OnImageClickListener {

    // Handles the logic for swapping images when one is dragged and dropped onto another
    private var mOnDragListener: View.OnDragListener = OnDragListener { v, event ->
        when (event.action) {
            DragEvent.ACTION_DROP -> {
                val targetView = v as? FrameImageView
                // Find exactly which frame the user dropped the image on
                val selectedView = targetView?.let { getSelectedFrameImageView(it, event) }

                if (selectedView != null) {
                    val draggedView = event.localState as FrameImageView
                    // Swap images if both views have a valid photoItem and paths are different
                    val targetPath = selectedView.photoItem?.imagePath ?: ""
                    val draggedPath = draggedView.photoItem?.imagePath ?: ""

                    if (targetPath != draggedPath) {
                        selectedView.swapImage(draggedView)
                    }
                }
            }
        }
        true
    }

    private val mItemImageViews: MutableList<FrameImageView> = ArrayList()
    private var mViewWidth: Int = 0
    private var mViewHeight: Int = 0
    private var mOutputScaleRatio = 1f
    private var mQuickActionClickListener: OnQuickActionClickListener? = null

    // Determine memory capacity to optimize image decoder sampler size
    private val isLowMemoryDevice: Boolean
        get() {
            val memoryInfo = ImageUtils.getMemoryInfo(context)
            // If total RAM is <= 1GB, we consider it a low memory device
            return memoryInfo.totalMem > 0 && (memoryInfo.totalMem / 1048576.0 <= 1024)
        }

    interface OnQuickActionClickListener {
        fun onEditActionClick(v: FrameImageView)
        fun onChangeActionClick(v: FrameImageView)
    }

    init {
        // Enable hardware acceleration for the layout to ensure smooth dragging and rendering
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    /**
     * Calculates which FrameImageView is currently under the touch point during a drag event.
     */
    private fun getSelectedFrameImageView(target: FrameImageView, event: DragEvent): FrameImageView? {
        val dragged = event.localState as FrameImageView
        val leftMargin = (mViewWidth * (target.photoItem?.bound?.left ?: 0f)).toInt()
        val topMargin = (mViewHeight * (target.photoItem?.bound?.top ?: 0f)).toInt()

        val globalX = leftMargin + event.x
        val globalY = topMargin + event.y

        // Iterate through views in reverse (top to bottom) to find the target
        for (idx in mItemImageViews.indices.reversed()) {
            val view = mItemImageViews[idx]
            val bound = view.photoItem?.bound ?: continue
            val x = globalX - mViewWidth * bound.left
            val y = globalY - mViewHeight * bound.top

            if (view.isSelected(x, y)) {
                return if (view === dragged) null else view
            }
        }
        return null
    }

    fun saveInstanceState(outState: Bundle) {
        mItemImageViews.forEach { it.saveInstanceState(outState) }
    }

    fun restoreInstanceState(savedInstanceState: Bundle) {
        mItemImageViews.forEach { it.restoreInstanceState(savedInstanceState) }
    }

    fun setQuickActionClickListener(quickActionClickListener: OnQuickActionClickListener) {
        mQuickActionClickListener = quickActionClickListener
    }

    /**
     * Builds the collage layout by inflating FrameImageViews based on the PhotoItem list.
     */
    @JvmOverloads
    fun build(viewWidth: Int, viewHeight: Int, outputScaleRatio: Float, space: Float = 0f, corner: Float = 0f) {
        if (viewWidth < 1 || viewHeight < 1) return

        mViewWidth = viewWidth
        mViewHeight = viewHeight
        mOutputScaleRatio = outputScaleRatio

        this.removeAllViews()
        mItemImageViews.clear()

        // Set sampler size based on complexity and memory to prevent OutOfMemoryError
        ImageDecoder.SAMPLER_SIZE = if (mPhotoItems.size > 4 || isLowMemoryDevice) 256 else 512

        for (item in mPhotoItems) {
            val imageView = addPhotoItemView(item, mOutputScaleRatio, space, corner)
            mItemImageViews.add(imageView)
        }
    }

    fun setSpace(space: Float, corner: Float) {
        mItemImageViews.forEach { it.setSpace(space, corner) }
    }

    private fun addPhotoItemView(item: PhotoItem, outputScaleRatio: Float, space: Float, corner: Float): FrameImageView {
        val imageView = FrameImageView(context, item)
        val bound = item.bound

        val leftMargin = (mViewWidth * bound.left).toInt()
        val topMargin = (mViewHeight * bound.top).toInt()

        // Calculate frame size based on percentage bounds
        val frameWidth = if (bound.right == 1f) (mViewWidth - leftMargin) else (mViewWidth * bound.width() + 0.5f).toInt()
        val frameHeight = if (bound.bottom == 1f) (mViewHeight - topMargin) else (mViewHeight * bound.height() + 0.5f).toInt()

        imageView.init(frameWidth.toFloat(), frameHeight.toFloat(), outputScaleRatio, space, corner)
        imageView.setOnImageClickListener(this)

        // Drag listener only needed if there is more than one photo to swap with
        if (mPhotoItems.size > 1) {
            imageView.setOnDragListener(mOnDragListener)
        }

        val params = LayoutParams(frameWidth, frameHeight).apply {
            this.leftMargin = leftMargin
            this.topMargin = topMargin
        }

        imageView.originalLayoutParams = params
        addView(imageView, params)
        return imageView
    }

    /**
     * Renders all collage frames into a single high-resolution Bitmap for saving.
     */
    @Throws(OutOfMemoryError::class)
    fun createImage(): Bitmap {
        val outWidth = (mOutputScaleRatio * mViewWidth).toInt()
        val outHeight = (mOutputScaleRatio * mViewHeight).toInt()

        val template = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(template)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        for (view in mItemImageViews) {
            val img = view.image
            if (img != null && !img.isRecycled) {
                val left = (view.left * mOutputScaleRatio)
                val top = (view.top * mOutputScaleRatio)
                val width = (view.width * mOutputScaleRatio)
                val height = (view.height * mOutputScaleRatio)

                canvas.save() // Modern alternative to saveLayer
                canvas.translate(left, top)
                canvas.clipRect(0f, 0f, width, height)
                view.drawOutputImage(canvas)
                canvas.restore()
            }
        }
        return template
    }

    fun recycleImages() {
        mItemImageViews.forEach { it.recycleImage() }
        System.gc()
    }

    override fun onLongClickImage(v: FrameImageView) {
        if (mPhotoItems.size > 1) {
            val photoItem = v.photoItem ?: return
            v.tag = "x=${photoItem.x},y=${photoItem.y},path=${photoItem.imagePath}"

            val item = ClipData.Item(v.tag.toString())
            val dragData = ClipData(v.tag.toString(), arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), item)
            val myShadow = DragShadowBuilder(v)

            // Use modern startDragAndDrop for API 24+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                v.startDragAndDrop(dragData, myShadow, v, 0)
            } else {
                @Suppress("DEPRECATION")
                v.startDrag(dragData, myShadow, v, 0)
            }
        }
    }

    override fun onDoubleClickImage(view: FrameImageView) {
        // Implementation for double tap (e.g., zoom reset or edit)
    }

    companion object {
        private val TAG = FramePhotoLayout::class.java.simpleName
    }
}