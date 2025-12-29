package com.photoeditor.photoeffect.adapter

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.photoeditor.photoeffect.R
import com.photoeditor.photoeffect.databinding.ItemFrameBinding // Ensure this matches your XML file name

class BackgroundAdapter(
    private val mContext: Context,
    private val bgListener: OnBGClickListener
) : RecyclerView.Adapter<BackgroundAdapter.BackgroundHolder>() {

    private var mImages: Array<String> = mContext.assets.list("background") ?: arrayOf()
    var selectedindex = 0

    interface OnBGClickListener {
        fun onBGClick(drawable: Drawable)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BackgroundHolder {
        // Using View Binding to inflate the layout
        val binding = ItemFrameBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BackgroundHolder(binding)
    }

    override fun getItemCount(): Int {
        return mImages.size
    }

    override fun onBindViewHolder(holder: BackgroundHolder, position: Int) {
        val imageName = mImages[position]

        // Logic remains exactly the same: Load from assets
        val inputStream = mContext.assets.open("background/$imageName")
        val drawable = Drawable.createFromStream(inputStream, null)

        // Accessing views through binding
        holder.binding.imgFrame.setImageDrawable(drawable)

        // Highlight logic
        if (selectedindex == position) {
            holder.binding.llItemframe.setBackgroundColor(
                ContextCompat.getColor(mContext, R.color.colorAccent)
            )
        } else {
            holder.binding.llItemframe.setBackgroundColor(
                ContextCompat.getColor(mContext, R.color.transparent)
            )
        }

        // Click logic
        holder.binding.imgFrame.setOnClickListener {
            selectedindex = position
            drawable?.let { bgListener.onBGClick(it) }
            notifyDataSetChanged()
        }
    }

    // ViewHolder updated to use Binding
    class BackgroundHolder(val binding: ItemFrameBinding) : RecyclerView.ViewHolder(binding.root)
}