package com.example.skysave.main.folder_recyclerview

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.example.skysave.R
import com.google.firebase.storage.StorageReference


class FolderAdapter(private val context: Context?, private val folders: ArrayList<StorageReference>, private val onFolderClickListener: OnFolderClickListener) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    interface OnFolderClickListener {
        fun onFolderClick(folder: StorageReference)
    }

    interface OnFolderLongClickListener {
        fun onFolderLongClick(position: Int)
    }

    private var folderLongClickListener: OnFolderLongClickListener? = null

    fun setOnFolderLongClickListener(listener: OnFolderLongClickListener) {
        folderLongClickListener = listener
    }

    inner class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val folderName: TextView = itemView.findViewById(R.id.folder_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_file_explorer, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = folders[position]

        holder.folderName.text = folder.name
        holder.folderName.textSize = 16F

        val blueBorderDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.TRANSPARENT)
            setStroke(2.dpToPx(), Color.BLUE)
            cornerRadius = 8.dpToPx().toFloat()
        }

        holder.folderName.background = blueBorderDrawable

        val layoutParams = holder.itemView.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.marginStart = 10.dpToPx()
        layoutParams.marginEnd = 10.dpToPx()
        holder.itemView.layoutParams = layoutParams

        holder.itemView.setOnClickListener {
            onFolderClickListener.onFolderClick(folder)
        }

        holder.itemView.setOnLongClickListener {
            folderLongClickListener?.onFolderLongClick(holder.bindingAdapterPosition)
            true
        }
    }

    override fun getItemCount(): Int = folders.size

    fun getItem(position: Int): StorageReference {
        return folders[position]
    }

    private fun Int.dpToPx(): Int {
        val density = Resources.getSystem().displayMetrics.density
        return (this * density).toInt()
    }

    fun addFolder(newFolder: StorageReference) {
        if(!folders.contains(newFolder)){
            folders.add(newFolder)
            notifyItemInserted(folders.size - 1)
        }
    }

    fun removeFolderAt(folderPos: Int) {
        folders.removeAt(folderPos)
        notifyItemRemoved(folderPos)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        val itemDecoration = DividerItemDecoration(context, DividerItemDecoration.HORIZONTAL)
        recyclerView.addItemDecoration(itemDecoration)
    }
}