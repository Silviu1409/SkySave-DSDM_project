package com.example.skysave.auth

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.skysave.AuthActivity
import com.example.skysave.R
import com.example.skysave.databinding.FragmentRegisterBinding
import com.example.skysave.datatypes.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import io.realm.RealmList
import java.io.ByteArrayOutputStream


class Register : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private lateinit var authActivityContext: AuthActivity


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)

        authActivityContext = (activity as AuthActivity)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val isInternetConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true


        binding.registerButton.setOnClickListener {
            val email = binding.registerEmail.text.toString()
            val password = binding.registerPassword.text.toString()
            val alias = binding.registerAlias.text.toString()

            authActivityContext.hideKeyboard()

            if (email == "" || password == "" || alias == ""){
                Log.d(authActivityContext.getTag(), "Empty field(s)!")
                Toast.makeText(activity, "Empty field(s)!", Toast.LENGTH_SHORT).show()

                return@setOnClickListener
            }

            requireActivity().let { activity ->
                FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(activity) { task ->
                        if (task.isSuccessful) {
                            val user = FirebaseAuth.getInstance().currentUser

                            if (user != null) {
                                val userDetails = User(user.uid, email, alias, RealmList())

                                authActivityContext.getRealm().executeTransactionAsync({ realm ->
                                    realm.copyToRealmOrUpdate(userDetails)
                                }, {
                                    val glide = Glide.with(this)

                                    val requestBuilder = glide.asBitmap()
                                        .load(R.drawable.default_icon)
                                        .apply(RequestOptions().override(75, 75))

                                    requestBuilder.into(object : CustomTarget<Bitmap>() {
                                        override fun onResourceReady(
                                            resource: Bitmap,
                                            transition: Transition<in Bitmap>?
                                        ) {
                                            val folderRef = authActivityContext.getStorage().child(user.uid)
                                            val imageRef = folderRef.child("icon.jpg")

                                            val baos = ByteArrayOutputStream()
                                            resource.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                                            val data = baos.toByteArray()

                                            val uploadTask = imageRef.putBytes(data)
                                            uploadTask.addOnSuccessListener {
                                                Log.d(authActivityContext.getTag(), "Created folder for user and registered successfully")

                                                Toast.makeText(activity, "Registered successfully! Go to the login page to log in.", Toast.LENGTH_LONG).show()

                                                binding.registerEmail.text?.clear()
                                                binding.registerPassword.text?.clear()
                                                binding.registerAlias.text?.clear()
                                            }.addOnFailureListener { e ->
                                                Log.e(authActivityContext.getErrTag(), "Error creating folder: ${e.message}")
                                                Toast.makeText(activity, "Couldn't create folder", Toast.LENGTH_SHORT).show()
                                            }
                                        }

                                        override fun onLoadCleared(placeholder: Drawable?) {
                                            Log.w(authActivityContext.getTag(), "Cancelled load")
                                        }
                                    })
                                }, {
                                    Log.e(authActivityContext.getErrTag(), "Error saving user data to Realm")
                                    Toast.makeText(activity, "Couldn't register", Toast.LENGTH_SHORT).show()
                                })
                            } else {
                                Log.e(authActivityContext.getErrTag(), "User wasn't created: ${task.exception}")
                                Toast.makeText(activity, "Couldn't register", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            if (!isInternetConnected) {
                                Log.e(authActivityContext.getErrTag(), "Missing network connection: ${task.exception}")
                                Toast.makeText(activity, "You are not connected to an internet connection", Toast.LENGTH_LONG).show()
                            } else {
                                try {
                                    throw task.exception!!
                                } catch (e: FirebaseAuthWeakPasswordException) {
                                    Log.e(authActivityContext.getErrTag(), "Weak password: ${e.message}")
                                    Toast.makeText(activity, "Password is too weak!", Toast.LENGTH_SHORT).show()
                                } catch (e: FirebaseAuthInvalidCredentialsException) {
                                    Log.e(authActivityContext.getErrTag(), "Invalid email format: ${e.message}")
                                    Toast.makeText(activity, "Invalid email format!", Toast.LENGTH_SHORT).show()
                                } catch (e: FirebaseAuthUserCollisionException) {
                                    Log.e(authActivityContext.getErrTag(), "Account already exists for this email: ${e.message}")
                                    Toast.makeText(activity, "An account for this email address already exists!", Toast.LENGTH_SHORT).show()
                                }
                                catch (e: Exception) {
                                    Log.e(authActivityContext.getErrTag(), "Error at register: ${e.message}")
                                    Toast.makeText(activity, "Couldn't register!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
            }
        }

        binding.RegisterToLogin.setOnClickListener {
            findNavController().navigate(R.id.action_Register_to_Login)
        }

        binding.RegisterToReset.setOnClickListener {
            findNavController().navigate(R.id.action_Register_to_ForgotPassword)
        }

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ) {
            val layoutParams = binding.registerTitle.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.topMargin = 100

            binding.registerTitle.layoutParams = layoutParams
        }
        else if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT){
            val layoutParams = binding.registerTitle.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.topMargin = 250

            binding.registerTitle.layoutParams = layoutParams
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}