package com.example.skysave.main

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.skysave.MainActivity
import com.example.skysave.R
import com.example.skysave.databinding.FragmentTrashBinding
import com.example.skysave.main.trash_recyclerview.TrashAdapter
import com.google.firebase.storage.StorageReference


class Trash : Fragment() {
    private var _binding: FragmentTrashBinding? = null
    private val binding get() = _binding!!

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TrashAdapter
    private lateinit var searchView: SearchView

    private lateinit var mainActivityContext: MainActivity


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrashBinding.inflate(inflater, container, false)

        mainActivityContext = (activity as MainActivity)
        mainActivityContext.setStorageFabVisibility(View.INVISIBLE)

        recyclerView = binding.trashList
        searchView = binding.searchBar
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = TrashAdapter(context, this, arrayListOf())
        recyclerView.adapter = adapter

        val filesRef = mainActivityContext.getFolderRef().child("trash")
        val query = filesRef.listAll()

        query.addOnSuccessListener { result ->
            adapter = TrashAdapter(context, this, result.items as ArrayList<StorageReference>)

            if  (adapter.itemCount == 0){
                binding.searchBarCard.visibility = View.INVISIBLE
                binding.noFilesText.visibility = View.VISIBLE
            } else {
                binding.searchBarCard.visibility = View.VISIBLE
                binding.noFilesText.visibility = View.INVISIBLE
            }

            recyclerView.adapter = adapter
        }.addOnFailureListener { e ->
            Log.e(mainActivityContext.getTag(), "Cannot display trash RecyclerView: ${e.message}")
        }

        binding.searchBar.setOnQueryTextListener(object : SearchView.OnQueryTextListener{
            override fun onQueryTextSubmit(query: String?): Boolean {
                mainActivityContext.hideKeyboard()
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                if(newText == ""){
                    this.onQueryTextSubmit("")
                }

                adapter.filter.filter(newText)
                return false
            }
        })

        return binding.root
    }

    fun updateText(){
        if (adapter.itemCount == 0){
            binding.noFilesText.text = getString(R.string.files_no_files_found)

            binding.noFilesText.visibility = View.VISIBLE

        } else {
            binding.noFilesText.text = getString(R.string.trash_no_files)

            binding.noFilesText.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}