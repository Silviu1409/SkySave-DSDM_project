package com.example.skysave.main.trash_recyclerview

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import com.example.skysave.R


class TrashViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val trashNameView: TextView = itemView.findViewById(R.id.trash_name)
    val trashRestoreView: ImageButton = itemView.findViewById(R.id.trash_restore)
    val trashRemoveView: ImageButton = itemView.findViewById(R.id.trash_remove_icon)
    val trashSizeView: AppCompatTextView = itemView.findViewById(R.id.trash_remove_size)
}