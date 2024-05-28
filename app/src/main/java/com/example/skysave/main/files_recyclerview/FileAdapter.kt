package com.example.skysave.main.files_recyclerview

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Filter
import android.widget.Filterable
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.skysave.MainActivity
import com.example.skysave.R
import com.example.skysave.main.Files
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.firebase.storage.StorageReference
import java.io.File
import java.util.*
import kotlin.math.roundToInt


class FileAdapter(private val context: Context?, private val fragment: Files, private val files: ArrayList<StorageReference>) : RecyclerView.Adapter<FileViewHolder>(), Filterable, Player.Listener {

    var filteredFileItems = files

    private val mainActivityContext = (context as MainActivity)
    private var starredFiles: List<String> = mainActivityContext.getUser()?.starred_files ?: listOf()
    private val fileDir = mainActivityContext.getFileDir()


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

    @SuppressLint("NotifyDataSetChanged")
    fun filterStarred(isStarred: Boolean){
        var filteredList = mutableListOf<StorageReference>()

        if (!isStarred){
            filteredList = files
        } else {
            for (item in files){
                if (item.toString() in starredFiles){
                    filteredList.add(item)
                }
            }
        }

        filteredFileItems = filteredList as ArrayList<StorageReference>
        fragment.updateText()
        notifyDataSetChanged()
    }

    override fun getFilter(): Filter {
        return filter
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = filteredFileItems[position]
        holder.fileNameView.text = file.name

        val localFile = File(fileDir, file.name)
        val tempLocalFile = File(context!!.cacheDir, file.name)

        if (file.toString() in starredFiles) {
            holder.fileStarView.setImageResource(R.drawable.icon_starred_filled)
        } else {
            holder.fileStarView.setImageResource(R.drawable.icon_starred_empty)
        }

        file.metadata.addOnSuccessListener { metadata ->

            // check if file has been already downloaded and check if the file size is the same
            if (fileDir.exists() && fileDir.isDirectory) {
                if (localFile.exists() && localFile.length() == metadata.sizeBytes) {
                    //if file has been already downloaded, remove the download button
                    holder.fileDownloadView.visibility = View.GONE
                    holder.fileViewView.visibility = View.VISIBLE

                    Log.d(mainActivityContext.getTag(), "${file.name} was already downloaded")
                } else {
                    holder.fileDownloadView.visibility = View.VISIBLE

                    Log.w(mainActivityContext.getTag(), "${file.name} was not downloaded previously")
                }
            } else {
                Log.e(mainActivityContext.getErrTag(), "Folder does not exist")
            }

            val fileSizeInBytes = metadata.sizeBytes.toDouble()
            val fileSize = mainActivityContext.getReadableFileSize(fileSizeInBytes)
            holder.fileDownloadSizeView.text = fileSize

            if (metadata.contentType?.startsWith("image/") == true) {
                holder.filePreviewView.visibility = View.VISIBLE
                holder.filePreviewView.setImageResource(R.drawable.image_preview)

                if(tempLocalFile.exists() && tempLocalFile.length() == metadata.sizeBytes){
                    Log.d(mainActivityContext.getTag(), "File is already cached")

                    Glide.with(holder.itemView)
                        .load(tempLocalFile)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(object : CustomTarget<Drawable>() {
                            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                                val scale = holder.fileContentView.height.toFloat() / resource.intrinsicHeight.toFloat()
                                val scaledWidth = (scale * resource.intrinsicWidth).toInt()

                                val layoutParams = holder.fileContentView.layoutParams
                                layoutParams.width = scaledWidth
                                holder.fileContentView.layoutParams = layoutParams

                                holder.filePreviewView.setImageDrawable(resource)
                            }

                            override fun onLoadCleared(placeholder: Drawable?) { }
                        })
                } else {
                    Glide.with(holder.itemView)
                        .load(file)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(object : CustomTarget<Drawable>() {
                            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                                val scale = holder.fileContentView.height.toFloat() / resource.intrinsicHeight.toFloat()
                                val scaledWidth = (scale * resource.intrinsicWidth).toInt()

                                val layoutParams = holder.fileContentView.layoutParams
                                layoutParams.width = scaledWidth
                                holder.fileContentView.layoutParams = layoutParams

                                holder.filePreviewView.setImageDrawable(resource)
                            }

                            override fun onLoadCleared(placeholder: Drawable?) { }
                        })

                    file.getFile(tempLocalFile)
                        .addOnSuccessListener {
                            Log.d(mainActivityContext.getTag(), "Saved file to cache!")
                        }
                        .addOnFailureListener { e ->
                            Log.e(mainActivityContext.getErrTag(), "Failed to save file to cache: ${e.message}")
                        }
                }
            } else if (metadata.contentType?.startsWith("audio/") == true || metadata.contentType?.startsWith("video/") == true) {

                if (metadata.contentType?.startsWith("audio/") == true) {
                    holder.filePreviewView.setImageResource(R.drawable.audio_preview)
                }
                else if (metadata.contentType?.startsWith("video/") == true) {
                    holder.filePreviewView.setImageResource(R.drawable.video_preview)
                }

                if(tempLocalFile.exists() && tempLocalFile.length() == metadata.sizeBytes){
                    Log.d(mainActivityContext.getTag(), "File is already cached")

                    holder.filePreviewView.visibility = View.GONE
                    holder.filePlayerView.visibility = View.VISIBLE

                    val player = ExoPlayer.Builder(context).build()
                    val mediaItem = MediaItem.fromUri(tempLocalFile.toURI().toString())

                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.addListener(this)

                    holder.filePlayerView.player = player
                }
                else {
                    Log.w(mainActivityContext.getTag(), "File is not cached")

                    file.getFile(tempLocalFile)
                        .addOnSuccessListener {
                            holder.filePreviewView.visibility = View.GONE
                            holder.filePlayerView.visibility = View.VISIBLE

                            val player = ExoPlayer.Builder(context).build()
                            val mediaItem = MediaItem.fromUri(tempLocalFile.toURI().toString())

                            player.setMediaItem(mediaItem)
                            player.prepare()
                            player.addListener(this)

                            holder.filePlayerView.player = player

                            Log.d(mainActivityContext.getTag(), "File is now cached!")
                        }
                        .addOnFailureListener { e ->
                            holder.filePreviewView.visibility = View.VISIBLE
                            holder.filePlayerView.visibility = View.GONE

                            Log.e(mainActivityContext.getErrTag(), "Failed to cache file: ${e.message}")
                        }
                }
            } else {
                holder.filePreviewView.visibility = View.VISIBLE
                holder.filePlayerView.visibility = View.GONE

                val layoutParams = holder.fileContentView.layoutParams
                layoutParams.width = 100.dpToPx()
                layoutParams.height = 100.dpToPx()
                holder.fileContentView.layoutParams = layoutParams

                holder.filePreviewView.setImageResource(R.drawable.file_preview)
            }
        }

        holder.fileStarView.setOnClickListener {
            if (file.toString() !in starredFiles) {
                Toast.makeText(context, "File added to starred!", Toast.LENGTH_SHORT).show()

                val aux = starredFiles.toMutableList()
                aux.add(file.toString())
                starredFiles = aux.toList()
                mainActivityContext.getUser()?.starred_files = starredFiles

                mainActivityContext.getDb().collection("users")
                    .document(mainActivityContext.getUser()!!.uid)
                    .update("starred_files", starredFiles)
                    .addOnSuccessListener {
                        Log.d(mainActivityContext.getTag(), "Added starred file ref to db")
                    }
                    .addOnFailureListener { e ->
                        Log.e(mainActivityContext.getErrTag(), "Failed to add starred file ref to db: ${e.message}")
                    }

                val emptyStar = ContextCompat.getDrawable(context, R.drawable.icon_starred_empty)
                val fullStar = ContextCompat.getDrawable(context, R.drawable.icon_starred_filled)

                holder.fileStarView.setImageDrawable(emptyStar)

                val anim = ObjectAnimator.ofPropertyValuesHolder(
                    holder.fileStarView,
                    PropertyValuesHolder.ofFloat("scaleX", 0f, 1f),
                    PropertyValuesHolder.ofFloat("scaleY", 0f, 1f)
                )
                anim.duration = 300
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        holder.fileStarView.setImageDrawable(fullStar)
                    }
                })
                anim.start()
            } else {
                Toast.makeText(context, "File removed from starred!", Toast.LENGTH_SHORT).show()

                val aux = starredFiles.toMutableList()
                aux.remove(file.toString())
                starredFiles = aux.toList()
                mainActivityContext.getUser()?.starred_files = starredFiles

                mainActivityContext.getDb().collection("users")
                    .document(mainActivityContext.getUser()!!.uid)
                    .update("starred_files", starredFiles)
                    .addOnSuccessListener {
                        Log.d(mainActivityContext.getTag(), "Removed starred file ref from db")
                    }
                    .addOnFailureListener { e ->
                        Log.e(mainActivityContext.getErrTag(), "Failed to remove starred file ref from db: ${e.message}")
                    }

                val emptyStar = ContextCompat.getDrawable(context, R.drawable.icon_starred_empty)
                val fullStar = ContextCompat.getDrawable(context, R.drawable.icon_starred_filled)

                holder.fileStarView.setImageDrawable(fullStar)

                val anim = ObjectAnimator.ofPropertyValuesHolder(
                    holder.fileStarView,
                    PropertyValuesHolder.ofFloat("scaleX", 0f, 1f),
                    PropertyValuesHolder.ofFloat("scaleY", 0f, 1f)
                )
                anim.duration = 300
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        holder.fileStarView.setImageDrawable(emptyStar)
                    }
                })
                anim.start()
            }
        }

        holder.fileViewView.setOnClickListener {
            val context: Context = holder.itemView.context

            val fileUri = FileProvider.getUriForFile(context, context.packageName + ".provider", localFile)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(fileUri, MimeTypeMap.getSingleton().getMimeTypeFromExtension(localFile.extension))
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

            try {
                context.startActivity(Intent.createChooser(intent, "Open with"))
            } catch (e: ActivityNotFoundException) {
                Log.e(mainActivityContext.getErrTag(), "Activity not found: ${e.message}")
            }
        }

        holder.fileDownloadIconView.setOnClickListener {
            if (localFile.exists()) {
                localFile.delete()
            }

            if (tempLocalFile.exists() && tempLocalFile.length() > 0) {
                tempLocalFile.copyTo(localFile, true)
                Log.d(mainActivityContext.getTag(), "Got file from cache!")
                Toast.makeText(context, "${file.name} downloaded!", Toast.LENGTH_SHORT).show()

                holder.fileDownloadView.visibility = View.GONE
                holder.fileViewView.visibility = View.VISIBLE
            } else {
                notificationDownloadChannel("download_notification_channel", NotificationManager.IMPORTANCE_LOW)

                val notificationId = 0

                val notificationBuilder = NotificationCompat.Builder(context, "download_notification_channel")
                    .setContentTitle("Downloading ${file.name}")
                    .setSmallIcon(R.drawable.icon_notification)
                    .setProgress(100, 0, false)
                    .setPriority(NotificationCompat.PRIORITY_LOW)

                val notificationManager = context.getSystemService(NotificationManager::class.java)
                notificationManager.notify(notificationId, notificationBuilder.build())

                file.getFile(localFile)
                    .addOnProgressListener { snapshot ->
                        val progress = (100.0 * snapshot.bytesTransferred / snapshot.totalByteCount).toInt()
                        notificationBuilder.setProgress(100, progress, false).setContentText("$progress%")
                        notificationManager.notify(notificationId, notificationBuilder.build())
                    }
                    .addOnSuccessListener {

                        notificationBuilder.setContentText("Download complete")
                            .setProgress(0, 0, false)
                        notificationManager.notify(notificationId, notificationBuilder.build())

                        val context: Context = holder.itemView.context

                        val fileUri = FileProvider.getUriForFile(context, context.packageName + ".provider", localFile)
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setDataAndType(fileUri, MimeTypeMap.getSingleton().getMimeTypeFromExtension(localFile.extension))
                        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

                        notificationDownloadChannel("download_notification_channel_2", NotificationManager.IMPORTANCE_HIGH)

                        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                        val completedNotificationBuilder = NotificationCompat.Builder(context, "download_notification_channel_2")
                            .setSmallIcon(R.drawable.icon_notification)
                            .setContentTitle("File download Complete")
                            .setContentText("Your file has been downloaded successfully.")
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                        notificationManager.notify(notificationId, completedNotificationBuilder.build())

                        Log.d(mainActivityContext.getTag(), "File downloaded successfully!")
                        Toast.makeText(context, "${file.name} downloaded!", Toast.LENGTH_SHORT).show()

                        holder.fileDownloadView.visibility = View.GONE
                        holder.fileViewView.visibility = View.VISIBLE
                    }
                    .addOnFailureListener { e ->
                        notificationBuilder
                            .setContentText("Download failed: ${e.message}")
                            .setProgress(0, 0, false)

                        notificationManager.notify(notificationId, notificationBuilder.build())

                        Log.e(mainActivityContext.getErrTag(), "Failed to download file: ${e.message}")
                    }
            }
        }

        holder.fileShareView.setOnClickListener {
            file.downloadUrl
                .addOnSuccessListener { uri ->
                    val fileUrl = uri.toString()
                    val fileType = "text/html"


                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = fileType
                        putExtra(Intent.EXTRA_TITLE, file.name)
                        putExtra(Intent.EXTRA_TEXT, fileUrl)
                    }

                    val chooser = Intent.createChooser(sendIntent, "Share file...")
                    holder.itemView.context.startActivity(chooser)

                    Log.d(mainActivityContext.getTag(), "File shared")
                }
                .addOnFailureListener { e ->
                    Log.e(mainActivityContext.getErrTag(), "Failed to get file download url: ${e.message}")
                }
        }

        holder.fileTrashView.setOnClickListener {
            val newFile = mainActivityContext.getFolderRef().child("trash/${file.name}")

            file.getFile(tempLocalFile)
                .addOnSuccessListener {
                    newFile.putFile(Uri.fromFile(tempLocalFile))
                        .addOnSuccessListener {
                            files.remove(file)
                            notifyItemRemoved(position)

                            val aux = starredFiles.toMutableList()
                            aux.remove(file.toString())
                            starredFiles = aux.toList()
                            mainActivityContext.getUser()?.starred_files = starredFiles

                            mainActivityContext.getDb().collection("users")
                                .document(mainActivityContext.getUser()!!.uid)
                                .update("starred_files", starredFiles)
                                .addOnSuccessListener {
                                    tempLocalFile.delete()
                                    file.delete()

                                    Log.d(mainActivityContext.getTag(), "File moved successfully!")
                                    Toast.makeText(context, "${newFile.name} moved to trash!", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { e ->
                                    Log.e(mainActivityContext.getErrTag(), "Failed to remove starred file ref from db: ${e.message}")
                                }
                        }
                        .addOnFailureListener { e ->
                            tempLocalFile.delete()

                            Log.e(mainActivityContext.getErrTag(), "Failed to move file: ${e.message}")
                        }
                }
                .addOnFailureListener { e ->
                    tempLocalFile.delete()

                    Log.e(mainActivityContext.getErrTag(), "Failed to move file: ${e.message}")
                }
        }
    }

    private fun notificationDownloadChannel(channelId: String, importance: Int) {
        val channelName = "Download Notification Channel"
        val channelDescription = "Shows download progress while a file is being downloaded from Firebase Storage"
        val channel = NotificationChannel(channelId, channelName, importance)
        channel.description = channelDescription
        val notificationManager = context!!.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    fun addItem(newFile: StorageReference){
        files.add(newFile)
        val index = filteredFileItems.size - 1
        notifyItemInserted(index)
    }

    override fun getItemCount() = filteredFileItems.size

    private fun Int.dpToPx(): Int {
        val density = Resources.getSystem().displayMetrics.density
        return (this * density).roundToInt()
    }
}