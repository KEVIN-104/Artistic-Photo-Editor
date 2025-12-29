package com.photoeditor.photoeffect.adapter

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.photoeditor.photoeffect.R
import com.photoeditor.photoeffect.databinding.ItemColorBinding
import java.lang.Exception

class StickerAdapter(private val mContext: Context, private val categoryPosition: Int) :
    RecyclerView.Adapter<StickerAdapter.StickerHolder>() {

    private var images: Array<String>
    private val names: Array<String> = arrayOf("emoji", "cat", "dog", "chicken", "texts", "tusk")
    private var selectedindex = 0
    private var stickerListener: StickerListener? = null

    init {
        // Maintain the logic: Select the folder based on the passed position
        images = if (categoryPosition in names.indices) {
            mContext.assets.list(names[categoryPosition]) ?: arrayOf()
        } else {
            arrayOf()
        }
    }

    interface StickerListener {
        fun onStickerClick(view: View, drawable: Drawable)
    }

    fun setOnStickerClick(stickerlistener: StickerListener) {
        this.stickerListener = stickerlistener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StickerHolder {
        // Use View Binding to inflate the layout
        val binding = ItemColorBinding.inflate(LayoutInflater.from(mContext), parent, false)
        return StickerHolder(binding)
    }

    override fun getItemCount(): Int {
        return images.size
    }

    override fun onBindViewHolder(holder: StickerHolder, position: Int) {
        try {
            val path = names[categoryPosition] + "/" + images[position]
            val inputStream = mContext.assets.open(path)
            val drawable = Drawable.createFromStream(inputStream, null)

            // Access views through binding
            holder.binding.imgColor.setImageDrawable(drawable)

            // Selection Logic
            if (selectedindex == position) {
                holder.binding.llColor.setBackgroundColor(
                    ContextCompat.getColor(mContext, R.color.colorAccent)
                )
            } else {
                holder.binding.llColor.setBackgroundColor(
                    ContextCompat.getColor(mContext, R.color.transparent)
                )
            }

            // Click Logic
            holder.binding.imgColor.setOnClickListener { view ->
                selectedindex = position
                try {
                    drawable?.let {
                        stickerListener?.onStickerClick(view, it)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                notifyDataSetChanged()
            }

            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ViewHolder holds the binding object
    class StickerHolder(val binding: ItemColorBinding) : RecyclerView.ViewHolder(binding.root)
}