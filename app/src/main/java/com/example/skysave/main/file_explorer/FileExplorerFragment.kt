package com.example.skysave.main.file_explorer

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.skysave.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage


class FileExplorerFragment : DialogFragment() {

    interface OnFolderSelectedListener {
        fun onFolderSelected(folderPath: String)
    }

    private var onFolderSelectedListener: OnFolderSelectedListener? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var pathTextView: TextView
    private lateinit var goToParentButton: MaterialButton
    private lateinit var createFolderButton: MaterialButton
    private lateinit var selectFolderButton: MaterialButton

    private var currentPath: String = ""
    private var rootPath: String = ""
    private var action: String = ""

    private val dummyFilePaths: HashSet<String> = HashSet()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_file_explorer, container, false)

        arguments?.let {
            action = it.getString(ARG_ACTION, "")
            currentPath = it.getString(ARG_PATH, "")
            rootPath = it.getString(ARG_ROOT_PATH, "")
        }

        recyclerView = view.findViewById(R.id.file_explorer_list)
        pathTextView = view.findViewById(R.id.pathTextView)
        goToParentButton = view.findViewById(R.id.go_to_parent)
        createFolderButton = view.findViewById(R.id.create_folder)
        selectFolderButton = view.findViewById(R.id.select_folder)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        createFolderButton.setOnClickListener {
            showCreateFolderDialog()
        }

        selectFolderButton.setOnClickListener {
            onFolderSelectedListener?.onFolderSelected(currentPath)
            dismiss()
        }

        loadFolderContents()
        updateUI()

        return view
    }

    private fun loadFolderContents() {
        val displayPath = currentPath.removePrefix(rootPath).let {
            it.ifEmpty { "/" }
        }
        pathTextView.text = getString(R.string.current_path, displayPath)

        val storageRef = Firebase.storage.reference.child(currentPath)

        storageRef.listAll().addOnSuccessListener { result ->
            val folders = result.prefixes
            val adapter = FileExplorerAdapter(folders) { selectedFolder ->
                currentPath = selectedFolder.path
                loadFolderContents()
                updateUI()
            }
            recyclerView.adapter = adapter
        }.addOnFailureListener { exception ->
            Toast.makeText(requireContext(), "Failed to load folder contents: ${exception.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI() {
        if(currentPath.contentEquals(rootPath)){
            goToParentButton.isEnabled = false
            selectFolderButton.isEnabled = false
        }
        else{
            goToParentButton.isEnabled = true
            selectFolderButton.isEnabled = true
            goToParentButton.setOnClickListener {
                navigateToParentFolder()
            }
        }
    }

    private fun navigateToParentFolder() {
        val parentPath = currentPath.substringBeforeLast('/')
        if (parentPath.isNotEmpty() && parentPath != "uid") {
            currentPath = parentPath
            loadFolderContents()
            updateUI()
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
        val newFolderRef = Firebase.storage.reference.child("$currentPath/$folderName")
        newFolderRef.child(".dummy").putBytes(byteArrayOf()).addOnSuccessListener {
            dummyFilePaths.add("$currentPath/$folderName/.dummy")

            loadFolderContents()
            updateUI()
        }.addOnFailureListener { exception ->
            Toast.makeText(requireContext(), "Failed to create folder: ${exception.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun setOnFolderSelectedListener(listener: OnFolderSelectedListener) {
        onFolderSelectedListener = listener
    }

    companion object {
        private const val ARG_ACTION = "action"
        private const val ARG_PATH = "path"
        private const val ARG_ROOT_PATH = "path"

        fun newInstance(action: String, path: String, rootPath: String): FileExplorerFragment {
            val fragment = FileExplorerFragment()
            val args = Bundle()
            args.putString(ARG_ACTION, action)
            args.putString(ARG_PATH, path)
            args.putString(ARG_ROOT_PATH, rootPath)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        removeDummyFiles()
    }

    private fun removeDummyFiles() {
        for (dummyFilePath in dummyFilePaths) {
            val fileRef = Firebase.storage.reference.child(dummyFilePath)
            fileRef.delete()
        }
    }
}