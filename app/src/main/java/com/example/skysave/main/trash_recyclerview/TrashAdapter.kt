package com.example.skysave.main.trash_recyclerview

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.skysave.MainActivity
import com.example.skysave.R
import com.example.skysave.main.Trash
import com.google.firebase.storage.StorageReference
import java.io.File
import java.util.*


class TrashAdapter(private val context: Context?, private val fragment: Trash, private val files: ArrayList<StorageReference>) : RecyclerView.Adapter<TrashViewHolder>(), Filterable {

    var filteredFileItems = files

    private val mainActivityContext = (context as MainActivity)

    private val filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val filteredList = if (constraint.isNullOrBlank()) {
                files
            } else {
                val filterPattern = constraint.toString().lowercase(Locale.getDefault())

                files.filter { item ->
                    item.name.lowercase(Locale.getDefault()).contains(filterPattern)
                }
            }

            val results = FilterResults()
            results.values = filteredList
            return results
        }

        @Suppress("UNCHECKED_CAST")
        @SuppressLint("NotifyDataSetChanged")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            filteredFileItems = results?.values as ArrayList<StorageReference>
            fragment.updateText()
            notifyDataSetChanged()
        }
    }

    override fun getFilter(): Filter {
        return filter
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrashViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trash, parent, false)
        return TrashViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrashViewHolder, position: Int) {
        val file = filteredFileItems[position]
        holder.trashNameView.text = file.name

        var fileSizeInBytes = 0L

        file.metadata
            .addOnSuccessListener { metadata ->
                fileSizeInBytes = metadata.sizeBytes
                val fileSize = mainActivityContext.getReadableFileSize(fileSizeInBytes.toDouble())
                holder.trashSizeView.text = fileSize
            }
            .addOnFailureListener { e ->
                Log.e(mainActivityContext.getErrTag(), "Failed to get file metadata: ${e.message}")
            }

        holder.trashRestoreView.setOnClickListener {
            val newFile = mainActivityContext.getFolderRef().child("files/${file.name}")

            val localFile = File(context!!.cacheDir, file.name)

            file.getFile(localFile).addOnSuccessListener {
                newFile.putFile(Uri.fromFile(localFile)).addOnSuccessListener {
                    file.delete().addOnSuccessListener {
                        files.remove(file)
                        notifyItemRemoved(position)
                        localFile.delete()
                        Toast.makeText(context, "File restored!", Toast.LENGTH_SHORT).show()

                        Log.d(mainActivityContext.getTag(), "File restored successfully.")
                    }
                    .addOnFailureListener { e ->
                        Log.e(mainActivityContext.getErrTag(), "Failed to delete the original file: ${e.message}")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(mainActivityContext.getErrTag(), "Failed to move the file: ${e.message}")
                }
            }
            .addOnFailureListener { e ->
                Log.e(mainActivityContext.getErrTag(), "Failed to download the source file: ${e.message}")
            }
        }

        holder.trashRemoveView.setOnClickListener {
            file.delete()
                .addOnSuccessListener {
                    mainActivityContext.changeFolderSize(-fileSizeInBytes)
                    mainActivityContext.changePreferencesFolderSize()
                    mainActivityContext.setStorageSpaceUsed(mainActivityContext.getReadableFileSize(mainActivityContext.getFolderSize().toDouble()))

                    files.remove(file)
                    notifyItemRemoved(position)
                    Toast.makeText(context, "File removed from cloud storage!", Toast.LENGTH_SHORT).show()

                    Log.d(mainActivityContext.getTag(), "File removed successfully.")
                }
                .addOnFailureListener {  e ->
                    Log.e(mainActivityContext.getErrTag(), "Failed to remove file: ${e.message}")
                }
        }
    }

    override fun getItemCount() = filteredFileItems.size
}