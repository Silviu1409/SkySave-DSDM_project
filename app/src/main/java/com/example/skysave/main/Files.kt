package com.example.skysave.main

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.skysave.MainActivity
import com.example.skysave.R
import com.example.skysave.databinding.FragmentFilesBinding
import com.example.skysave.main.files_recyclerview.FileAdapter
import com.google.firebase.storage.StorageReference


class Files : Fragment() {
    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileAdapter
    private lateinit var searchView: SearchView
    private lateinit var searchStarredView: AppCompatImageButton

    private lateinit var mainActivityContext: MainActivity

    private var isStarred: Boolean = false


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)

        mainActivityContext = (activity as MainActivity)
        mainActivityContext.setStorageFabVisibility(View.VISIBLE)

        recyclerView = binding.filesList
        searchView = binding.searchBar
        searchStarredView = binding.searchStarred
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = FileAdapter(context, this, arrayListOf())
        recyclerView.adapter = adapter

        val filesRef = mainActivityContext.getFolderRef().child("files")
        val query = filesRef.listAll()

        query.addOnSuccessListener { result ->
            adapter = FileAdapter(context, this, result.items as ArrayList<StorageReference>)

            if  (adapter.itemCount == 0){
                binding.searchBarCard.visibility = View.INVISIBLE
                binding.searchStarredCard.visibility = View.INVISIBLE
                binding.noFilesText.visibility = View.VISIBLE
            } else {
                binding.searchBarCard.visibility = View.VISIBLE
                binding.searchStarredCard.visibility = View.VISIBLE
                binding.noFilesText.visibility = View.INVISIBLE
            }

            recyclerView.adapter = adapter
        }.addOnFailureListener { e ->
            Log.e(mainActivityContext.getTag(), "Cannot display file RecyclerView: ${e.message}")
        }

        binding.searchBar.setOnQueryTextListener(object : SearchView.OnQueryTextListener{
            override fun onQueryTextSubmit(query: String?): Boolean {
                mainActivityContext.hideKeyboard()
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                binding.searchStarred.setImageResource(R.drawable.icon_starred_empty)

                if(newText == ""){
                    this.onQueryTextSubmit("")
                }

                adapter.filter.filter(newText)
                return false
            }
        })

        binding.searchStarred.setOnClickListener {
            if (!isStarred) {
                isStarred = true
                binding.searchStarred.setImageResource(R.drawable.icon_starred_filled)
            } else {
                isStarred = false
                binding.searchStarred.setImageResource(R.drawable.icon_starred_empty)
            }
            adapter.filterStarred(isStarred)
        }

        return binding.root
    }

    fun updateText(){
        if (adapter.itemCount == 0){
            binding.noFilesText.text = getString(R.string.files_no_files_found)

            binding.noFilesText.visibility = View.VISIBLE
        } else {
            binding.noFilesText.text = getString(R.string.files_no_files)

            binding.noFilesText.visibility = View.INVISIBLE
        }
    }

    fun refreshRecyclerView(newFile: StorageReference) {
        if  (adapter.itemCount==0){
            binding.noFilesText.visibility = View.INVISIBLE
        }

        adapter.addItem(newFile)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}