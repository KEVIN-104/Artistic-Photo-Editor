package com.photoeditor.photoeffect.template

import android.content.Context
import android.graphics.*
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import com.photoeditor.photoeffect.multitouch.MultiTouchHandler
import com.photoeditor.photoeffect.utils.*

/**
 * Custom ImageView for handling individual photo items within a template.
 * Supports multi-touch (zoom, pan, rotate), masking, and long-press/double-tap gestures.
 */
class ItemImageView(context: Context, val photoItem: PhotoItem) : androidx.appcompat.widget.AppCompatImageView(context) {

    private val mGestureDetector: GestureDetector
    private var mTouchHandler: MultiTouchHandler? = null

    var image: Bitmap? = null
    var maskImage: Bitmap? = null
        private set

    private val mPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val mImageMatrix: Matrix = Matrix()
    val scaleMatrix: Matrix = Matrix()
    private val maskMatrix: Matrix = Matrix()
    private val scaleMaskMatrix: Matrix = Matrix()

    var viewWidth: Float = 0f
        private set
    var viewHeight: Float = 0f
        private set

    private var mOutputScale = 1f
    private var mOnImageClickListener: OnImageClickListener? = null
    private var mOriginalLayoutParams: RelativeLayout.LayoutParams? = null
    private var mEnableTouch = true

    /**
     * Property to store and retrieve original layout dimensions for restoration.
     */
    var originalLayoutParams: RelativeLayout.LayoutParams
        get() {
            return mOriginalLayoutParams?.let {
                val params = RelativeLayout.LayoutParams(it.width, it.height)
                params.leftMargin = it.leftMargin
                params.topMargin = it.topMargin
                params
            } ?: (layoutParams as RelativeLayout.LayoutParams)
        }
        set(value) {
            mOriginalLayoutParams = RelativeLayout.LayoutParams(value.width, value.height).apply {
                leftMargin = value.leftMargin
                topMargin = value.topMargin
            }
        }

    interface OnImageClickListener {
        fun onLongClickImage(view: ItemImageView)
        fun onDoubleClickImage(view: ItemImageView)
    }

    init {
        // 1. Load Main Image from ResultContainer or Disk
        photoItem.imagePath?.takeIf { it.isNotEmpty() }?.let { path ->
            image = ResultContainer.getInstance().getImage(path)
            if (image == null || image?.isRecycled == true) {
                image = ImageDecoder.decodeFileToBitmap(path)
                image?.let { ResultContainer.getInstance().putImage(path, it) }
            }
        }

        // 2. Load Mask Image
        photoItem.maskPath?.takeIf { it.isNotEmpty() }?.let { path ->
            maskImage = ResultContainer.getInstance().getImage(path)
            if (maskImage == null || maskImage?.isRecycled == true) {
                maskImage = PhotoUtils.decodePNGImage(context, path)
                maskImage?.let { ResultContainer.getInstance().putImage(path, it) }
            }
        }

        // 3. Configure UI
        scaleType = ScaleType.MATRIX
        setLayerType(View.LAYER_TYPE_HARDWARE, mPaint)

        // 4. Setup Gesture Detection
        mGestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                mOnImageClickListener?.onLongClickImage(this@ItemImageView)
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                mOnImageClickListener?.onDoubleClickImage(this@ItemImageView)
                return true
            }
        })
    }

    /**
     * Swaps the bitmap and data with another ItemImageView (used for drag-and-drop).
     */
    fun swapImage(otherView: ItemImageView) {
        val tempBitmap = otherView.image
        otherView.image = image
        image = tempBitmap

        val tempPath = otherView.photoItem.imagePath
        otherView.photoItem.imagePath = photoItem.imagePath
        photoItem.imagePath = tempPath

        resetImageMatrix()
        otherView.resetImageMatrix()
    }

    fun setOnImageClickListener(listener: OnImageClickListener) {
        mOnImageClickListener = listener
    }

    override fun getImageMatrix(): Matrix = mImageMatrix

    /**
     * Initializes the view dimensions and initial transformation matrices.
     */
    fun init(w: Float, h: Float, scale: Float) {
        this.viewWidth = w
        this.viewHeight = h
        this.mOutputScale = scale

        // Center main image
        image?.let { bm ->
            mImageMatrix.set(ImageUtils.createMatrixToDrawImageInCenterView(w, h, bm.width.toFloat(), bm.height.toFloat()))
            scaleMatrix.set(ImageUtils.createMatrixToDrawImageInCenterView(scale * w, scale * h, bm.width.toFloat(), bm.height.toFloat()))
        }

        // Center mask image
        maskImage?.let { msk ->
            maskMatrix.set(ImageUtils.createMatrixToDrawImageInCenterView(w, h, msk.width.toFloat(), msk.height.toFloat()))
            scaleMaskMatrix.set(ImageUtils.createMatrixToDrawImageInCenterView(scale * w, scale * h, msk.width.toFloat(), msk.height.toFloat()))
        }

        mTouchHandler = MultiTouchHandler().apply {
            setMatrices(mImageMatrix, scaleMatrix)
            setScale(scale)
            setEnableRotation(true)
        }
        invalidate()
    }

    fun setImagePath(path: String) {
        photoItem.imagePath = path
        recycleMainImage()
        image = ImageDecoder.decodeFileToBitmap(path)

        image?.let { bm ->
            mImageMatrix.set(ImageUtils.createMatrixToDrawImageInCenterView(viewWidth, viewHeight, bm.width.toFloat(), bm.height.toFloat()))
            scaleMatrix.set(ImageUtils.createMatrixToDrawImageInCenterView(mOutputScale * viewWidth, mOutputScale * viewHeight, bm.width.toFloat(), bm.height.toFloat()))
            mTouchHandler?.setMatrices(mImageMatrix, scaleMatrix)
            ResultContainer.getInstance().putImage(path, bm)
        }
        invalidate()
    }

    fun resetImageMatrix() {
        image?.let { bm ->
            mImageMatrix.set(ImageUtils.createMatrixToDrawImageInCenterView(viewWidth, viewHeight, bm.width.toFloat(), bm.height.toFloat()))
            scaleMatrix.set(ImageUtils.createMatrixToDrawImageInCenterView(mOutputScale * viewWidth, mOutputScale * viewHeight, bm.width.toFloat(), bm.height.toFloat()))
            mTouchHandler?.setMatrices(mImageMatrix, scaleMatrix)
            invalidate()
        }
    }

    fun clearMainImage() {
        photoItem.imagePath = null
        recycleMainImage()
        invalidate()
    }

    private fun recycleMainImage() {
        if (image != null && image?.isRecycled == false) {
            image?.recycle()
            image = null
            System.gc()
        }
    }

    private fun recycleMaskImage() {
        if (maskImage != null && maskImage?.isRecycled == false) {
            maskImage?.recycle()
            maskImage = null
            System.gc()
        }
    }

    fun recycleImages(shouldRecycleMain: Boolean) {
        if (shouldRecycleMain) recycleMainImage()
        recycleMaskImage()
    }


    override fun onDraw(canvas: Canvas) {
        val bm = image
        val msk = maskImage

        if (bm != null && !bm.isRecycled && msk != null && !msk.isRecycled) {
            // Draw original image
            canvas.drawBitmap(bm, mImageMatrix, mPaint)

            // Apply DST_IN PorterDuff mode to "cut" the image using the mask
            mPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawBitmap(msk, maskMatrix, mPaint)
            mPaint.xfermode = null
        } else if (bm != null && !bm.isRecycled) {
            // Fallback: draw only image if no mask is present
            canvas.drawBitmap(bm, mImageMatrix, mPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!mEnableTouch) return super.onTouchEvent(event)

        mGestureDetector.onTouchEvent(event)

        val bm = image
        if (mTouchHandler != null && bm != null && !bm.isRecycled) {
            mTouchHandler?.touch(event)
            // Synchronize the transformation matrices
            mImageMatrix.set(mTouchHandler!!.matrix)
            scaleMatrix.set(mTouchHandler!!.scaleMatrix)
            invalidate()
            return true
        }
        return true
    }

    fun setEnableTouch(enabled: Boolean) {
        mEnableTouch = enabled
    }

    companion object {
        private val TAG = ItemImageView::class.java.simpleName
    }
}