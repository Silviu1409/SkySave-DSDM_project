package com.example.skysave.main

import android.app.NotificationManager
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.SearchView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.skysave.MainActivity
import com.example.skysave.R
import com.example.skysave.databinding.FragmentFilesBinding
import com.example.skysave.main.file_explorer.FileExplorerFragment
import com.example.skysave.main.files_recyclerview.FileAdapter
import com.example.skysave.main.folder_recyclerview.FolderAdapter
import com.google.android.material.button.MaterialButton
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import java.util.Locale


class Files : Fragment(), FileAdapter.OnFileLongClickListener, FolderAdapter.OnFolderClickListener, FolderAdapter.OnFolderLongClickListener {
    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!

    private lateinit var goToParentButton: MaterialButton
    private lateinit var folderAdapter: FolderAdapter
    private lateinit var recyclerViewFolder: RecyclerView
    private lateinit var fileAdapter: FileAdapter
    private lateinit var recyclerViewFile: RecyclerView
    private lateinit var searchView: SearchView
    private lateinit var searchStarredView: AppCompatImageButton
    private lateinit var addFolderButton: MaterialButton

    private lateinit var rootFolder: StorageReference
    private lateinit var currentFolder: StorageReference

    private lateinit var mainActivityContext: MainActivity

    private var isStarred: Boolean = false

    private var starredFiles: List<String> = listOf()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)

        mainActivityContext = (activity as MainActivity)
        mainActivityContext.setStorageFabVisibility(View.VISIBLE)

        goToParentButton = binding.goToParent
        recyclerViewFolder = binding.foldersList
        recyclerViewFile = binding.filesList
        searchView = binding.searchBar
        searchStarredView = binding.searchStarred
        addFolderButton = binding.createFolderButton
        addFolderButton.setOnClickListener {
            showCreateFolderDialog()
        }
        recyclerViewFile.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewFolder.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        fileAdapter = FileAdapter(context, this, arrayListOf(), starredFiles)
        recyclerViewFile.adapter = fileAdapter

        folderAdapter = FolderAdapter(context, arrayListOf(), this)
        recyclerViewFolder.adapter = folderAdapter

        rootFolder = mainActivityContext.getFolderRef().child("files")
        currentFolder = rootFolder

        loadFolderContents(rootFolder)

        goToParentButton.setOnClickListener {
            navigateToParentFolder()
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener{
            override fun onQueryTextSubmit(query: String?): Boolean {
                mainActivityContext.hideKeyboard()
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                searchStarredView.setImageResource(R.drawable.icon_starred_empty)

                if(newText == ""){
                    this.onQueryTextSubmit("")
                }

                fileAdapter.filter.filter(newText)
                return false
            }
        })

        searchStarredView.setOnClickListener {
            isStarred = !isStarred
            searchStarredView.isSelected = isStarred
            fileAdapter.filterStarred(isStarred)
        }

        return binding.root
    }

    fun updateText(){
        if (fileAdapter.itemCount == 0){
            binding.noFilesText.text = getString(R.string.files_no_files_found)

            binding.noFilesText.visibility = View.VISIBLE
        } else {
            binding.noFilesText.text = getString(R.string.files_no_files)

            binding.noFilesText.visibility = View.INVISIBLE
        }
    }

    fun refreshRecyclerView(newFile: StorageReference) {
        if (fileAdapter.itemCount==0){
            binding.noFilesText.visibility = View.INVISIBLE
        }

        fileAdapter.addItem(newFile)
    }

    fun removeItemFromRecyclerView(oldFile: StorageReference) {
        fileAdapter.removeItem(oldFile)

        if (fileAdapter.itemCount==0){
            binding.noFilesText.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onFileLongClick(position: Int) {
        val itemView = recyclerViewFile.layoutManager?.findViewByPosition(position)
        itemView?.let { view ->
            val popupMenu = PopupMenu(requireContext(), view)
            popupMenu.menuInflater.inflate(R.menu.menu_options_file, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_copy -> {
                        showFileExplorer("copy", position)
                        true
                    }
                    R.id.action_move -> {
                        showFileExplorer("move", position)
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }
    }

    private fun showFileExplorer(action: String, position: Int) {
        val fileRef = fileAdapter.getItem(position)
        val fileExplorerFragment = FileExplorerFragment.newInstance(action, rootFolder.path, rootFolder.path)

        fileExplorerFragment.setOnFolderSelectedListener(object : FileExplorerFragment.OnFolderSelectedListener {
            override fun onFolderSelected(folderPath: String) {
                mainActivityContext.let { main ->
                    val destinationRef = Firebase.storage.reference.child("$folderPath/${fileRef.name}")

                    // Fetch file metadata to get the file size
                    fileRef.metadata.addOnSuccessListener { metadata ->
                        val fileSize = metadata.sizeBytes

                        // Check if there is enough available space
                        if (main.getFolderSize() + fileSize > main.getFolderSizeLimit()) {
                            Toast.makeText(requireContext(), "Cannot copy/move file, storage limit reached!", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        // Check if the file already exists at the destination
                        destinationRef.downloadUrl.addOnSuccessListener {
                            Toast.makeText(requireContext(), "File already exists!", Toast.LENGTH_SHORT).show()
                        }.addOnFailureListener {
                            val notificationId = 0
                            val notificationBuilder = NotificationCompat.Builder(requireActivity(), "upload_notification_channel")
                                .setContentTitle("${when (action.lowercase(Locale.ROOT)) {
                                    "copy" -> "Copying"
                                    else -> "Moving"
                                }} ${fileRef.name}")
                                .setSmallIcon(R.drawable.icon_notification)
                                .setProgress(100, 0, false)
                                .setPriority(NotificationCompat.PRIORITY_LOW)

                            val notificationManager = requireContext().getSystemService(NotificationManager::class.java)
                            notificationManager.notify(notificationId, notificationBuilder.build())

                            fileRef.getBytes(Long.MAX_VALUE).addOnSuccessListener { bytes ->
                                val uploadTask = destinationRef.putBytes(bytes)
                                uploadTask.addOnProgressListener { snapshot ->
                                    val progress = (100.0 * snapshot.bytesTransferred / snapshot.totalByteCount).toInt()
                                    notificationBuilder.setProgress(100, progress, false).setContentText("$progress%")
                                    notificationManager.notify(notificationId, notificationBuilder.build())
                                }.addOnSuccessListener {
                                    notificationBuilder.setContentText("${action.replaceFirstChar {
                                        if (it.isLowerCase()) it.titlecase(
                                            Locale.ROOT
                                        ) else it.toString()
                                    }} complete")
                                        .setProgress(0, 0, false)
                                    notificationManager.notify(notificationId, notificationBuilder.build())

                                    if (action == "move") {
                                        fileRef.delete().addOnSuccessListener {
                                            Toast.makeText(requireContext(), "File moved successfully", Toast.LENGTH_SHORT).show()
                                        }.addOnFailureListener { exception ->
                                            Toast.makeText(requireContext(), "Failed to delete original file: ${exception.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(requireContext(), "File copied successfully", Toast.LENGTH_SHORT).show()
                                    }

                                    if(action == "copy"){
                                        // Update folder size and UI
                                        val newFolderSize: Long = main.getFolderSize() + fileSize
                                        main.changePreferencesFolderSize()
                                        main.setStorageSpaceUsed(mainActivityContext.getReadableFileSize(newFolderSize.toDouble()))
                                    }
                                    else{
                                        val starredFiles: HashSet<String> = mainActivityContext.getUser()?.starred_files?.toHashSet() ?: HashSet()

                                        if(starredFiles.contains(fileRef.toString())){
                                            starredFiles.remove(fileRef.toString())
                                            starredFiles.add(destinationRef.toString())

                                            mainActivityContext.getUser()?.starred_files = starredFiles.toList()

                                            mainActivityContext.getDb().collection("users")
                                                .document(mainActivityContext.getUser()!!.uid)
                                                .update("starred_files", starredFiles.toList())

                                            mainActivityContext.getSharedPreferencesUser().edit().putStringSet("starred_files",
                                                java.util.HashSet(mainActivityContext.getUser()!!.starred_files)).apply()
                                        }

                                        removeItemFromRecyclerView(fileRef)
                                    }

                                    if(folderPath.startsWith(currentFolder.path) && folderPath.count { it == '/' } == currentFolder.path.count { it == '/' } + 1){
                                        folderAdapter.addFolder(currentFolder.child(folderPath))
                                    }
                                }.addOnFailureListener { exception ->
                                    notificationBuilder.setContentText("${action.replaceFirstChar {
                                        if (it.isLowerCase()) it.titlecase(
                                            Locale.ROOT
                                        ) else it.toString()
                                    }} failed: ${exception.message}")
                                        .setProgress(0, 0, false)
                                    notificationManager.notify(notificationId, notificationBuilder.build())
                                    Toast.makeText(requireContext(), "Failed to $action file: ${exception.message}", Toast.LENGTH_SHORT).show()
                                }
                            }.addOnFailureListener { exception ->
                                Toast.makeText(requireContext(), "Failed to read file: ${exception.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }.addOnFailureListener { exception ->
                        Toast.makeText(requireContext(), "Failed to get file metadata: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })

        fileExplorerFragment.show(parentFragmentManager, "fileExplorer")
    }

    fun updateStarredFiles(newStarredFiles: List<String>) {
        starredFiles = newStarredFiles
        if (::fileAdapter.isInitialized) {
            fileAdapter.updateStarredFiles(newStarredFiles)
        }
    }

    private fun loadFolderContents(folder: StorageReference) {
        currentFolder = folder

        updateGoToParentButtonVisibility()

        val query = currentFolder.listAll()

        query.addOnSuccessListener { result ->
            val filteredItems = result.items.filter { !it.name.endsWith(".dummy") }
            fileAdapter = FileAdapter(context, this, filteredItems as ArrayList<StorageReference>, starredFiles)
            folderAdapter = FolderAdapter(context, result.prefixes as ArrayList<StorageReference>, this)

            if  (fileAdapter.itemCount + folderAdapter.itemCount == 0){
                binding.searchLayout.visibility = View.GONE
                binding.searchBarCard.visibility = View.GONE
                binding.searchStarredCard.visibility = View.GONE
                binding.noFilesText.visibility = View.VISIBLE
            } else {
                binding.searchLayout.visibility = View.VISIBLE
                binding.searchBarCard.visibility = View.VISIBLE
                binding.searchStarredCard.visibility = View.VISIBLE
                binding.noFilesText.visibility = View.INVISIBLE
            }

            if(fileAdapter.itemCount == 0){
                binding.filesList.visibility = View.GONE
            }
            else{
                binding.filesList.visibility = View.VISIBLE
            }

            if(folderAdapter.itemCount == 0){
                binding.foldersList.visibility = View.GONE
            }
            else{
                binding.foldersList.visibility = View.VISIBLE
            }

            recyclerViewFile.adapter = fileAdapter
            recyclerViewFolder.adapter = folderAdapter
            fileAdapter.setOnFileLongClickListener(this)
            folderAdapter.setOnFolderLongClickListener(this)
        }.addOnFailureListener { e ->
            Log.e(mainActivityContext.getTag(), "Cannot display file RecyclerView: ${e.message}")
        }
    }

    private fun navigateToParentFolder() {
        if (currentFolder != rootFolder) {
            val parentRef = currentFolder.parent
            loadFolderContents(parentRef!!)
        }
    }

    private fun updateGoToParentButtonVisibility() {
        goToParentButton.visibility = if (currentFolder != rootFolder) View.VISIBLE else View.GONE
    }

    override fun onFolderClick(folder: StorageReference) {
        currentFolder = folder
        loadFolderContents(currentFolder)
    }

    override fun onFolderLongClick(position: Int) {
        val itemView = recyclerViewFolder.layoutManager?.findViewByPosition(position)
        itemView?.let { view ->
            val popupMenu = PopupMenu(requireContext(), view)
            popupMenu.menuInflater.inflate(R.menu.menu_options_folder, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_copy -> {
                        showFolderExplorer("copy", position)
                        true
                    }
                    R.id.action_move -> {
                        showFolderExplorer("move", position)
                        true
                    }
                    R.id.action_delete -> {
                        confirmDeleteFolder(position)
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }
    }

    private fun showFolderExplorer(action: String, position: Int) {
        val folderRef = folderAdapter.getItem(position)
        val fileExplorerFragment = FileExplorerFragment.newInstance(action, rootFolder.path, rootFolder.path)

        fileExplorerFragment.setOnFolderSelectedListener(object : FileExplorerFragment.OnFolderSelectedListener {
            override fun onFolderSelected(folderPath: String) {
                mainActivityContext.let {
                    val destinationRef = Firebase.storage.reference.child("$folderPath/${folderRef.name}")

                    destinationRef.listAll().addOnSuccessListener {
                        Toast.makeText(requireContext(), "Folder already exists!", Toast.LENGTH_SHORT).show()
                    }.addOnFailureListener {
                        copyOrMoveFolder(folderRef, destinationRef, action) { success ->
                            if (success) {
                                if (action == "move") {
                                    deleteFolderRecursively(folderRef) { deleteSuccess ->
                                        if (deleteSuccess) {
                                            Toast.makeText(requireContext(), "Folder moved successfully", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(requireContext(), "Failed to delete original folder", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(requireContext(), "Folder copied successfully", Toast.LENGTH_SHORT).show()
                                }

                                loadFolderContents(currentFolder)
                            } else {
                                Toast.makeText(requireContext(), "Failed to $action folder", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        })

        fileExplorerFragment.show(parentFragmentManager, "fileExplorer")
    }

    private fun copyOrMoveFolder(sourceFolder: StorageReference, destinationFolder: StorageReference, action: String, callback: (Boolean) -> Unit) {
        sourceFolder.listAll().addOnSuccessListener { result ->
            val items = result.items
            val prefixes = result.prefixes

            val totalOperations = items.size + prefixes.size
            var successfulOperations = 0

            if (totalOperations == 0) {
                callback(true)
                return@addOnSuccessListener
            }

            val checkCompletion = {
                if (successfulOperations == totalOperations) {
                    callback(true)
                }
            }

            for (file in items) {
                file.metadata.addOnSuccessListener { metadata ->
                    file.getBytes(metadata.sizeBytes).addOnSuccessListener { bytes ->
                        val destinationFileRef = destinationFolder.child(file.name)

                        destinationFileRef.putBytes(bytes).addOnSuccessListener {
                            mainActivityContext.changeFolderSize(metadata.sizeBytes)
                            mainActivityContext.changePreferencesFolderSize()
                            mainActivityContext.setStorageSpaceUsed(mainActivityContext.getReadableFileSize(mainActivityContext.getFolderSize().toDouble()))

                            if(action == "move" && starredFiles.contains(file.toString())){
                                val aux = starredFiles.toMutableList()
                                aux.add(file.toString())
                                starredFiles = aux.toList()
                                mainActivityContext.getUser()?.starred_files = starredFiles

                                mainActivityContext.getDb().collection("users")
                                    .document(mainActivityContext.getUser()!!.uid)
                                    .update("starred_files", starredFiles)
                                    .addOnSuccessListener {
                                        Log.d(mainActivityContext.getTag(), "Added starred file ref to db")
                                        mainActivityContext.getSharedPreferencesUser().edit().putStringSet("starred_files",
                                            java.util.HashSet(mainActivityContext.getUser()!!.starred_files)
                                        ).apply()
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(mainActivityContext.getErrTag(), "Failed to add starred file ref to db: ${e.message}")
                                    }
                            }

                            successfulOperations++
                            checkCompletion()
                        }.addOnFailureListener {
                            callback(false)
                            return@addOnFailureListener
                        }
                    }.addOnFailureListener {
                        callback(false)
                        return@addOnFailureListener
                    }
                }.addOnFailureListener {
                    callback(false)
                }
            }

            for (folder in prefixes) {
                val destinationSubFolderRef = destinationFolder.child(folder.name)
                copyOrMoveFolder(folder, destinationSubFolderRef, action) { success ->
                    if (success) {
                        successfulOperations++
                        checkCompletion()
                    } else {
                        callback(false)
                        @Suppress("LABEL_NAME_CLASH")
                        return@copyOrMoveFolder
                    }
                }
            }
        }.addOnFailureListener {
            callback(false)
        }
    }

    private fun confirmDeleteFolder(position: Int) {
        val folderRef = folderAdapter.getItem(position)

        AlertDialog.Builder(requireContext())
            .setTitle("Delete Folder")
            .setMessage("Are you sure you want to delete this folder and all its contents?")
            .setPositiveButton("Yes") { dialog, _ ->
                deleteFolderRecursively(folderRef) { success ->
                    if (success) {
                        Toast.makeText(requireContext(), "Folder deleted successfully", Toast.LENGTH_SHORT).show()
                        folderAdapter.removeFolderAt(position)
                        if(folderAdapter.itemCount == 0){
                            binding.foldersList.visibility = View.GONE
                        }
                    } else {
                        Toast.makeText(requireContext(), "Failed to delete folder", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun deleteFolderRecursively(folder: StorageReference, callback: (Boolean) -> Unit) {
        folder.listAll().addOnSuccessListener { result ->
            val items = result.items
            val prefixes = result.prefixes

            val totalOperations = items.size + prefixes.size
            var successfulOperations = 0

            if (totalOperations == 0) {
                callback(true)
                return@addOnSuccessListener
            }

            val checkCompletion = {
                if (successfulOperations == totalOperations) {
                    callback(true)
                }
            }

            for (file in items) {
                file.metadata.addOnSuccessListener { metadata ->
                    file.delete().addOnSuccessListener {
                        mainActivityContext.changeFolderSize(-metadata.sizeBytes)
                        mainActivityContext.changePreferencesFolderSize()
                        mainActivityContext.setStorageSpaceUsed(mainActivityContext.getReadableFileSize(mainActivityContext.getFolderSize().toDouble()))

                        if(starredFiles.contains(file.toString())){
                            val aux = starredFiles.toMutableList()
                            aux.remove(file.toString())
                            starredFiles = aux.toList()
                            mainActivityContext.getUser()?.starred_files = starredFiles

                            mainActivityContext.getDb().collection("users")
                                .document(mainActivityContext.getUser()!!.uid)
                                .update("starred_files", starredFiles)
                                .addOnSuccessListener {
                                    Log.d(mainActivityContext.getTag(), "Removed starred file ref from db")
                                    mainActivityContext.getSharedPreferencesUser().edit().putStringSet("starred_files",
                                        java.util.HashSet(mainActivityContext.getUser()!!.starred_files)
                                    ).apply()
                                }
                                .addOnFailureListener { e ->
                                    Log.e(mainActivityContext.getErrTag(), "Failed to remove starred file ref from db: ${e.message}")
                                }
                        }

                        successfulOperations++
                        checkCompletion()
                    }.addOnFailureListener {
                        callback(false)
                        return@addOnFailureListener
                    }
                }.addOnFailureListener {
                    callback(false)
                }
            }

            for (subFolder in prefixes) {
                deleteFolderRecursively(subFolder) { success ->
                    if (success) {
                        successfulOperations++
                        checkCompletion()
                    } else {
                        callback(false)
                        @Suppress("LABEL_NAME_CLASH")
                        return@deleteFolderRecursively
                    }
                }
            }
        }.addOnFailureListener {
            callback(false)
        }
    }

    private fun showCreateFolderDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Create New Folder")

        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)

        builder.setPositiveButton("Create") { dialog, _ ->
            val folderName = input.text.toString()
            if (folderName.isNotEmpty()) {
                createNewFolder(folderName)
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun createNewFolder(folderName: String) {
        val newFolderRef = Firebase.storage.reference.child("${currentFolder.path}/$folderName")
        newFolderRef.child(".dummy").putBytes(byteArrayOf()).addOnSuccessListener {
            folderAdapter.addFolder(newFolderRef)
            binding.foldersList.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Created new folder!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { exception ->
            Toast.makeText(requireContext(), "Failed to create folder: ${exception.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun getCurrentFolder(): StorageReference {
        return currentFolder
    }
}