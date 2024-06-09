@file:Suppress("DEPRECATION")

package com.example.skysave.main.files_recyclerview

import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.skysave.R
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.material.card.MaterialCardView


class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val fileNameView: TextView = itemView.findViewById(R.id.file_name)

    val fileStarView: ImageButton = itemView.findViewById(R.id.file_star)

    val fileViewView: ImageButton = itemView.findViewById(R.id.file_view)

    val fileDownloadView: LinearLayoutCompat = itemView.findViewById(R.id.file_download)
    val fileDownloadIconView: ImageButton = itemView.findViewById(R.id.file_download_icon)
    val fileDownloadSizeView: TextView = itemView.findViewById(R.id.file_download_size)

    val fileShareView: ImageButton = itemView.findViewById(R.id.file_share)
    val fileTrashView: ImageButton = itemView.findViewById(R.id.file_trash)

    val fileContentView: MaterialCardView = itemView.findViewById(R.id.file_content)
    val filePlayerView: PlayerView = itemView.findViewById(R.id.file_player)
    val filePreviewView: ImageView = itemView.findViewById(R.id.file_preview)

    init {
        filePlayerView.visibility = View.INVISIBLE
        filePreviewView.visibility = View.VISIBLE

        filePlayerView.setShowNextButton(false)
        filePlayerView.setShowPreviousButton(false)

        itemView.setOnLongClickListener {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                itemView.isSelected = true
                true
            } else {
                false
            }
        }
    }
}