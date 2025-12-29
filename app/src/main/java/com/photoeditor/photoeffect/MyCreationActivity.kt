package com.photoeditor.photoeffect

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.lang.Long.compare
import java.util.*
import kotlin.collections.ArrayList
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.widget.RelativeLayout
import android.widget.Toast
import com.photoeditor.photoeffect.MainActivity.Companion.isFromSaved
import com.photoeditor.photoeffect.databinding.ActivityMyCreationBinding // Binding Import

class MyCreationActivity : AppCompatActivity() {

    // 1. Declare the binding variable
    private lateinit var binding: ActivityMyCreationBinding

    lateinit var img_path: ArrayList<File_Model>
    private var mLastClickTime: Long = 0

    // Debounce logic for clicks
    private fun checkClick(): Boolean {
        if (SystemClock.elapsedRealtime() - mLastClickTime < 1000) {
            return false
        }
        mLastClickTime = SystemClock.elapsedRealtime()
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. Initialize Binding
        binding = ActivityMyCreationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 3. Access RecyclerView via binding
        binding.listCreation.layoutManager = GridLayoutManager(this, 2, GridLayoutManager.VERTICAL, false)

        LoadImages().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        Log.e("Page", "My creation")
    }

    inner class LoadImages : AsyncTask<Void, Void, Void?>() {
        override fun doInBackground(vararg params: Void?): Void? {
            updateFileList()
            return null
        }

        override fun onPostExecute(result: Void?) {
            super.onPostExecute(result)

            if (!::img_path.isInitialized || img_path.size == 0) {
                val builder = AlertDialog.Builder(this@MyCreationActivity)
                builder.setMessage("No Files Found").setCancelable(false)
                    .setPositiveButton("Ok") { dialog, _ ->
                        dialog.cancel()
                        onBackPressed()
                    }
                builder.create().show()
                return
            }

            // Set adapter via binding
            val creationAdapter = CreationAdapter(img_path)
            binding.listCreation.adapter = creationAdapter
        }
    }

    fun updateFileList() {
        img_path = ArrayList()

        // 1. Check the App-Specific directory (where saveBitmap currently saves)
        val appSpecificFolder = getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let {
            File(it, "ArtisticEditor")
        }

        // 2. Check the Legacy/Public directory (for older files)
        val publicFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ArtisticEditor")

        val allFiles = ArrayList<File>()

        // Add files from app-specific folder
        appSpecificFolder?.listFiles()?.let { allFiles.addAll(it) }

        // Add files from public folder if it exists
        if (publicFolder.exists()) {
            publicFolder.listFiles()?.let { allFiles.addAll(it) }
        }

        // Sort by Date (Last Modified)
        val fileDateCmp = Comparator<File> { f1, f2 ->
            f2.lastModified().compareTo(f1.lastModified())
        }

        Collections.sort(allFiles, fileDateCmp)

        for (file in allFiles) {
            if (file.extension.lowercase() in listOf("jpg", "jpeg", "png")) {
                val fileModel = File_Model().apply {
                    file_path = file.absolutePath
                    file_title = file.name
                }
                img_path.add(fileModel)
            }
        }
    }

    inner class File_Model {
        lateinit var file_path: String
        lateinit var file_title: String
    }

    inner class CreationAdapter(private val paths: ArrayList<File_Model>) :
        RecyclerView.Adapter<CreationAdapter.CreationHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CreationHolder {
            val view = LayoutInflater.from(this@MyCreationActivity)
                .inflate(R.layout.item_creation, parent, false)
            return CreationHolder(view)
        }

        override fun getItemCount(): Int = paths.size

        override fun onBindViewHolder(holder: CreationHolder, position: Int) {
            // Use a local reference to the data to avoid issues during re-binding
            val currentPath = paths[holder.adapterPosition]

            val dm = resources.displayMetrics
            val width = dm.widthPixels

            holder.img_creation.layoutParams = RelativeLayout.LayoutParams(width / 2, width / 2)

            // Loading images directly from file path
            holder.img_creation.setImageURI(Uri.fromFile(File(currentPath.file_path)))
            holder.txt_title.text = currentPath.file_title

            // Delete logic
            holder.img_dlt.setOnClickListener {
                if (!checkClick()) return@setOnClickListener

                // IMPORTANT: Get the most up-to-date position from the ViewHolder
                val currentPos = holder.adapterPosition

                // Check for NO_POSITION to avoid crashes during animations
                if (currentPos == RecyclerView.NO_POSITION) return@setOnClickListener

                AlertDialog.Builder(this@MyCreationActivity)
                    .setTitle("Delete Creation")
                    .setMessage("Are you sure you want to delete this image?")
                    .setPositiveButton("Yes") { dialog, _ ->
                        val file = File(paths[currentPos].file_path)

                        if (file.exists() && file.delete()) {
                            // 1. Remove from Data Source
                            paths.removeAt(currentPos)

                            // 2. Notify specific item removed (gives a nice animation)
                            notifyItemRemoved(currentPos)

                            // 3. Notify the range change so subsequent positions are recalculated
                            notifyItemRangeChanged(currentPos, paths.size)

                            // 4. Handle empty state if last item deleted
                            if (paths.isEmpty()) {
                                // Show the "No Files Found" dialog by re-running the check
                                LoadImages().execute()
                            }
                        } else {
                            Toast.makeText(this@MyCreationActivity, "Failed to delete file", Toast.LENGTH_SHORT).show()
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
                    .show()
            }

            // View logic
            holder.img_creation.setOnClickListener {
                if (!checkClick()) return@setOnClickListener
                val currentPos = holder.adapterPosition
                if (currentPos == RecyclerView.NO_POSITION) return@setOnClickListener

                val intent = Intent(this@MyCreationActivity, ShowImageActivity::class.java)
                intent.putExtra("image_uri", paths[currentPos].file_path)
                startActivity(intent)
                finish()
            }
        }

        inner class CreationHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val img_creation: ImageView = itemView.findViewById(R.id.img_creation)
            val img_dlt: ImageView = itemView.findViewById(R.id.img_dlt)
            val txt_title: TextView = itemView.findViewById(R.id.txt_title)
        }
    }
}