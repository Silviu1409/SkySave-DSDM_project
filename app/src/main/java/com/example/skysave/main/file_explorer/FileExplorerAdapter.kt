package com.example.skysave.main.file_explorer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.skysave.R
import com.google.firebase.storage.StorageReference


class FileExplorerAdapter(private val folders: List<StorageReference>, private val onFolderClick: (StorageReference) -> Unit) : RecyclerView.Adapter<FileExplorerAdapter.FolderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file_explorer, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = folders[position]
        holder.bind(folder)
    }

    override fun getItemCount(): Int = folders.size

    inner class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val folderNameView: TextView = itemView.findViewById(R.id.folder_name)

        fun bind(folder: StorageReference) {
            folderNameView.text = folder.name
            itemView.setOnClickListener {
                onFolderClick(folder)
            }
        }
    }
}