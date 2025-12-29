package com.photoeditor.photoeffect.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

object ImageUtils {



    fun getMemoryInfo(context: Context): MemoryInfo {
        val info = MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        info.availMem = memInfo.availMem
        info.totalMem = memInfo.totalMem // Requires API 16+
        return info
    }

    // Scoped storage friendly folder path
    fun getOutputCollageFolder(context: Context): String {
        val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "PhotoCollage")
        if (!folder.exists()) folder.mkdirs()
        return folder.absolutePath
    }

    private val MIN_OUTPUT_IMAGE_SIZE = 640.0f

    val usedMemorySize: Long
        get() {
            return try {
                val info = Runtime.getRuntime()
                info.totalMemory() - info.freeMemory()
            } catch (e: Exception) {
                -1L
            }
        }

    class MemoryInfo {
        var availMem: Long = 0
        var totalMem: Long = 0
    }

    fun loadImageWithGlide(context: Context, imageView: ImageView, uri: String?) {
        if (uri.isNullOrEmpty()) return

        when {
            uri.startsWith("http://") || uri.startsWith("https://") -> Glide.with(context).load(uri).into(imageView)
            uri.startsWith("drawable://") -> {
                val id = uri.substring("drawable://".length).toIntOrNull()
                id?.let { Glide.with(context).load(it).into(imageView) }
            }
            uri.startsWith("assets://") -> {
                val file = uri.substring("assets://".length)
                Glide.with(context).load(Uri.parse("file:///android_asset/$file")).into(imageView)
            }
            else -> Glide.with(context).load(File(uri)).into(imageView)
        }
    }

    fun calculateOutputScaleFactor(viewWidth: Int, viewHeight: Int): Float {
        val minSide = Math.min(viewWidth, viewHeight).toFloat()
        var ratio = minSide / MIN_OUTPUT_IMAGE_SIZE
        return if (ratio < 1 && ratio > 0) 1.0f / ratio else 1f
    }

    fun saveAndShare(context: Context, image: Bitmap) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "COLLAGE_$timeStamp.png"
            val photoFile = File(getOutputCollageFolder(context), fileName)

            FileOutputStream(photoFile).use { out ->
                image.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // Standard way to notify gallery in modern Android
            MediaScannerConnection.scanFile(context, arrayOf(photoFile.absolutePath), null, null)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                // REQUIRED: Use FileProvider for Android 7.0+
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    photoFile
                )
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    fun dpFromPx(context: Context, px: Float): Float = px / context.resources.displayMetrics.density
    fun pxFromDp(context: Context, dp: Float): Float = dp * context.resources.displayMetrics.density

    fun createMatrixToDrawImageInCenterView(
        viewWidth: Float, viewHeight: Float,
        imageWidth: Float, imageHeight: Float
    ): Matrix {
        val ratio = Math.max(viewWidth / imageWidth, viewHeight / imageHeight)
        val dx = (viewWidth - imageWidth) / 2.0f
        val dy = (viewHeight - imageHeight) / 2.0f
        return Matrix().apply {
            postTranslate(dx, dy)
            postScale(ratio, ratio, viewWidth / 2, viewHeight / 2)
        }
    }

    fun recycleImageView(iv: ImageView?) {
        iv?.apply {
            val background = background
            val d = drawable
            setBackgroundColor(Color.TRANSPARENT)
            setImageBitmap(null)

            if (background is BitmapDrawable) background.bitmap?.let { if (!it.isRecycled) it.recycle() }
            if (d is BitmapDrawable) d.bitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    fun saveBitmap(bitmap: Bitmap, path: String) {
        try {
            FileOutputStream(path).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getImageOrientation(context: Context, imagePath: String): Int {
        var orientation = getOrientationFromExif(imagePath)
        if (orientation < 0) {
            val uri = Uri.fromFile(File(imagePath))
            orientation = getOrientationFromMediaStore(context, uri)
        }
        return orientation
    }

    private fun getOrientationFromExif(imagePath: String): Int {
        return try {
            val exif = ExifInterface(imagePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                else -> 0
            }
        } catch (e: IOException) {
            0
        }
    }

    private fun getOrientationFromMediaStore(context: Context, imageUri: Uri?): Int {
        if (imageUri == null) return -1
        val projection = arrayOf(MediaStore.Images.ImageColumns.ORIENTATION)
        context.contentResolver.query(imageUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getInt(0)
        }
        return -1
    }

    /**
     * Stack Blur Algorithm by Mario Klingemann <mario@quasimondo.com>
     */
    fun fastblur(sentBitmap: Bitmap, radius: Int): Bitmap? {
        val bitmap = sentBitmap.copy(sentBitmap.config ?: Bitmap.Config.ARGB_8888, true)
        if (radius < 1) return null

        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int; var gsum: Int; var bsum: Int; var x: Int; var y: Int; var i: Int; var p: Int; var yp: Int; var yi: Int; var yw: Int
        val vmin = IntArray(Math.max(w, h))
        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (idx in 0 until 256 * divsum) dv[idx] = idx / divsum

        yi = 0
        yw = 0
        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int; var stackstart: Int; var sir: IntArray; var rbs: Int
        val r1 = radius + 1
        var routsum: Int; var goutsum: Int; var boutsum: Int; var rinsum: Int; var ginsum: Int; var binsum: Int

        for (yIdx in 0 until h) {
            rinsum = 0; ginsum = 0; binsum = 0; routsum = 0; goutsum = 0; boutsum = 0; rsum = 0; gsum = 0; bsum = 0
            for (idx in -radius..radius) {
                p = pix[yi + Math.min(wm, Math.max(idx, 0))]
                sir = stack[idx + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)
                rbs = r1 - Math.abs(idx)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (idx > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2] }
                else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2] }
            }
            stackpointer = radius
            for (xIdx in 0 until w) {
                r[yi] = dv[rsum]; g[yi] = dv[gsum]; b[yi] = dv[bsum]
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]
                if (yIdx == 0) vmin[xIdx] = Math.min(xIdx + radius + 1, wm)
                p = pix[yw + vmin[xIdx]]
                sir[0] = (p and 0xff0000) shr 16; sir[1] = (p and 0x00ff00) shr 8; sir[2] = (p and 0x0000ff)
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                rsum += rinsum; gsum += ginsum; bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]
                yi++
            }
            yw += w
        }

        for (xIdx in 0 until w) {
            rinsum = 0; ginsum = 0; binsum = 0; routsum = 0; goutsum = 0; boutsum = 0; rsum = 0; gsum = 0; bsum = 0
            yp = -radius * w
            for (idx in -radius..radius) {
                yi = Math.max(0, yp) + xIdx
                sir = stack[idx + radius]
                sir[0] = r[yi]; sir[1] = g[yi]; sir[2] = b[yi]
                rbs = r1 - Math.abs(idx)
                rsum += r[yi] * rbs; gsum += g[yi] * rbs; bsum += b[yi] * rbs
                if (idx > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2] }
                else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2] }
                if (idx < hm) yp += w
            }
            yi = xIdx
            stackpointer = radius
            for (yIdx in 0 until h) {
                pix[yi] = (-0x1000000 and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]
                if (xIdx == 0) vmin[yIdx] = Math.min(yIdx + r1, hm) * w
                p = xIdx + vmin[yIdx]
                sir[0] = r[p]; sir[1] = g[p]; sir[2] = b[p]
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                rsum += rinsum; gsum += ginsum; bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]
                yi += w
            }
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }

    fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = Math.min(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            color = -0xbdbdbe
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val rect = Rect(0, 0, size, size)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }
}