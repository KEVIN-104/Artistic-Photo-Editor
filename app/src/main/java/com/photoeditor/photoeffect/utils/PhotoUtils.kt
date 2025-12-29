package com.photoeditor.photoeffect.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.Bitmap.Config
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import java.io.File
import java.io.IOException

object PhotoUtils {
    const val EDITED_WHITE_IMAGE_SUFFIX = "_white.jpg"
    const val FLIP_VERTICAL = 1
    const val FLIP_HORIZONTAL = 2
    const val DRAWABLE_PREFIX = "drawable://"
    const val ASSET_PREFIX = "assets://"

    /**
     * Correctly adds image to Gallery using MediaStore (Scoped Storage compliant).
     */

    /**
     * Decodes a PNG image from Drawables, Assets, or Local File paths.
     */

    fun blurImage(bitmap: Bitmap, radius: Float): Bitmap? {
        return ImageUtils.fastblur(bitmap, radius.toInt())
    }

    /**
     * Calculates the scale ratio between the source image and target view
     */
    fun calculateScaleRatio(imgW: Int, imgH: Int, viewW: Int, viewH: Int): Float {
        val ratioWidth = imgW.toFloat() / viewW
        val ratioHeight = imgH.toFloat() / viewH
        // Returns the maximum ratio to ensure the image covers the view (CenterCrop logic)
        return Math.max(ratioWidth, ratioHeight)
    }
    fun decodePNGImage(context: Context, uri: String): Bitmap? {
        return when {
            uri.startsWith(DRAWABLE_PREFIX) -> {
                try {
                    val resId = uri.substring(DRAWABLE_PREFIX.length).toInt()
                    BitmapFactory.decodeResource(context.resources, resId)
                } catch (ex: Exception) {
                    null
                }
            }
            uri.startsWith(ASSET_PREFIX) -> {
                val path = uri.substring(ASSET_PREFIX.length)
                try {
                    context.assets.open(path).use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)
                    }
                } catch (e: IOException) {
                    null
                }
            }
            else -> {
                // Assuming it's a local file path
                BitmapFactory.decodeFile(uri)
            }
        }
    }


    fun addImageToGallery(filePath: String, context: Context) {
        val file = File(filePath)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                file.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(it, values, null, null)
            }
        }
    }

    /**
     * Fills the background of a bitmap (useful for PNG to JPG conversion).
     */
    fun fillBackgroundColorToImage(bitmap: Bitmap, color: Int): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(color)
        canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return result
    }

    @JvmOverloads
    fun rotateImage(src: Bitmap?, degs: Float, flip: Boolean = false): Bitmap? {
        if (src == null || (degs == 0f && !flip)) return src
        val matrix = Matrix().apply {
            postRotate(degs)
            if (flip) postScale(1f, -1f)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /**
     * Modern loading with Glide.
     */
    fun loadImageWithGlide(context: Context, imageView: ImageView, uri: String?) {
        if (uri.isNullOrEmpty()) return

        val request = Glide.with(context)
            .load(when {
                uri.startsWith(DRAWABLE_PREFIX) -> uri.substring(DRAWABLE_PREFIX.length).toIntOrNull()
                uri.startsWith(ASSET_PREFIX) -> Uri.parse("file:///android_asset/${uri.substring(ASSET_PREFIX.length)}")
                uri.startsWith("http") -> uri
                else -> File(uri)
            })
            .diskCacheStrategy(DiskCacheStrategy.ALL)

        if (uri.startsWith(ASSET_PREFIX)) {
            request.signature(ObjectKey(uri)).into(imageView)
        } else {
            request.into(imageView)
        }
    }

    /**
     * Flipping logic using Matrix postScale.
     */
    fun flip(src: Bitmap, type: Int): Bitmap? {
        val matrix = Matrix()
        when (type) {
            FLIP_VERTICAL -> matrix.postScale(1.0f, -1.0f)
            FLIP_HORIZONTAL -> matrix.postScale(-1.0f, 1.0f)
            else -> return null
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /**
     * Removes transparent borders from an image by finding the non-transparent bounding box.
     */
    fun cleanImage(bitmap: Bitmap): Bitmap {
        var firstX = 0; var lastX = bitmap.width
        var firstY = 0; var lastY = bitmap.height

        // Scan for boundaries
        for (x in 0 until bitmap.width) {
            if (!isTransparentColumn(bitmap, x)) { firstX = x; break }
        }
        for (x in bitmap.width - 1 downTo firstX) {
            if (!isTransparentColumn(bitmap, x)) { lastX = x; break }
        }
        for (y in 0 until bitmap.height) {
            if (!isTransparentRow(bitmap, y)) { firstY = y; break }
        }
        for (y in bitmap.height - 1 downTo firstY) {
            if (!isTransparentRow(bitmap, y)) { lastY = y; break }
        }

        return Bitmap.createBitmap(bitmap, firstX, firstY, lastX - firstX + 1, lastY - firstY + 1)
    }

    private fun isTransparentRow(bitmap: Bitmap, row: Int): Boolean {
        for (x in 0 until bitmap.width) if (bitmap.getPixel(x, row) != Color.TRANSPARENT) return false
        return true
    }

    private fun isTransparentColumn(bitmap: Bitmap, col: Int): Boolean {
        for (y in 0 until bitmap.height) if (bitmap.getPixel(col, y) != Color.TRANSPARENT) return false
        return true
    }

    /**
     * Masks an image using PorterDuff DST_IN (Intersection).
     */
    fun cropImage(mainImage: Bitmap, mask: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(mainImage.width, mainImage.height, Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        canvas.drawBitmap(mainImage, 0f, 0f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawBitmap(mask, 0f, 0f, paint)
        paint.xfermode = null

        return result
    }

    /**
     * Creates a circular version of a bitmap.
     */
    fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = Math.min(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = size / 2f

        canvas.drawCircle(radius, radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

        val srcRect = Rect((bitmap.width - size) / 2, (bitmap.height - size) / 2, (bitmap.width + size) / 2, (bitmap.height + size) / 2)
        val destRect = Rect(0, 0, size, size)
        canvas.drawBitmap(bitmap, srcRect, destRect, paint)

        return output
    }
}