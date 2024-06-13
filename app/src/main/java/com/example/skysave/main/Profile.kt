package com.example.skysave.main

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.skysave.AuthActivity
import com.example.skysave.MainActivity
import com.example.skysave.databinding.FragmentProfileBinding
import java.io.ByteArrayOutputStream
import java.io.File


class Profile : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var mainActivityContext: MainActivity

    private lateinit var getProfileIcon: ActivityResultLauncher<String>


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)

        mainActivityContext = (activity as MainActivity)
        mainActivityContext.setStorageFabVisibility(View.INVISIBLE)

        getProfileIcon = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { changeProfileIcon(it) }
        }

        val tempLocalFile = File(requireContext().cacheDir, "icon.jpg")

        val imageRef = mainActivityContext.getFolderRef().child("icon.jpg")

        imageRef.metadata
            .addOnSuccessListener {  metadata ->

                if(tempLocalFile.exists() && tempLocalFile.length() == metadata.sizeBytes){
                    Log.d(mainActivityContext.getTag(), "Profile logo is already cached")

                    Glide.with(this)
                        .asBitmap()
                        .load(tempLocalFile)
                        .circleCrop()
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(binding.profileIcon)
                } else {
                    Log.w(mainActivityContext.getTag(), "Profile logo is not cached")

                    Glide.with(this)
                        .asBitmap()
                        .load(imageRef)
                        .circleCrop()
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(binding.profileIcon)

                    imageRef.getFile(tempLocalFile)
                        .addOnSuccessListener {
                            Log.d(mainActivityContext.getTag(), "Saved file to cache!")
                        }
                        .addOnFailureListener { e ->
                            Log.e(mainActivityContext.getErrTag(), "Failed to save file to cache: ${e.message}")
                        }
                }
            }
            .addOnFailureListener {  e ->
                Log.e(mainActivityContext.getErrTag(), "Failed to get icon metadata: ${e.message}")
            }

        binding.profileAlias.setText(mainActivityContext.getUser()!!.alias)
        binding.profileEmail.text = binding.profileEmail.text.toString().plus(" ").plus(mainActivityContext.getUser()!!.email)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.profileIcon.setOnClickListener {
            getProfileIcon.launch("image/*")
        }

        binding.profileAlias.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val newUserAlias = binding.profileAlias.text.toString()
                mainActivityContext.getUser()!!.alias = newUserAlias

                mainActivityContext.getRealm().executeTransactionAsync ({ realm ->
                    mainActivityContext.getUser()?.let { userObj ->
                        realm.copyToRealmOrUpdate(userObj)
                    }
                }, {
                    Log.d(mainActivityContext.getTag(), "Updated user alias!")

                    mainActivityContext.getSharedPreferencesUser().edit().putString("alias", newUserAlias).apply()
                    val newFolder = File(mainActivityContext.getFileDir().parentFile, newUserAlias)
                    mainActivityContext.getFileDir().renameTo(newFolder)
                    mainActivityContext.setFileDir(newFolder)
                    Log.d(mainActivityContext.getTag(), "Renamed user folder!")

                    Toast.makeText(context, "Updated alias!", Toast.LENGTH_SHORT).show()
                }, {
                    Log.e(mainActivityContext.getErrTag(), "Could not update user alias: $it")
                    Toast.makeText(context, "Cannot update alias!", Toast.LENGTH_SHORT).show()
                })

                mainActivityContext.hideKeyboard()

                true
            }
            else {
                false
            }
        }

        binding.profileLogout.setOnClickListener {
            mainActivityContext.removePreferencesUser()
            mainActivityContext.removePreferencesStorage()
            mainActivityContext.getAuth().signOut()

            val intent = Intent(activity, AuthActivity::class.java)
            intent.putExtra("logout", true)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()

        binding.profileAlias.setText(mainActivityContext.getUser()!!.alias)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun changeProfileIcon(uri: Uri) {
        val storageRef = mainActivityContext.getFolderRef().child("icon.jpg")

        requireActivity().contentResolver.openInputStream(uri)?.use { inputStream ->
            val bitmap = BitmapFactory.decodeStream(inputStream)

            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 75, 75, false)

            val baos = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
            val imgData = baos.toByteArray()

            storageRef.putBytes(imgData)
                .addOnSuccessListener {
                    Glide.with(this)
                        .load(storageRef)
                        .transform(CircleCrop())
                        .into(binding.profileIcon)
                }
                .addOnFailureListener { e ->
                    Log.e(mainActivityContext.getErrTag(), "Error uploading image: ${e.message}")
                }
        }
    }
}