package com.example.skysave.auth

import android.content.Context
//import android.content.Intent
import android.content.res.Configuration
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
import com.example.skysave.datatypes.User
import com.example.skysave.AuthActivity
//import com.example.skysave.MainActivity
import com.example.skysave.R
import com.example.skysave.databinding.FragmentLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException


class Login : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var authActivityContext: AuthActivity


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)

        authActivityContext = (activity as AuthActivity)

        return binding.root
    }

    @Suppress("UNCHECKED_CAST")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loginButtonGoogle.setOnClickListener {
            authActivityContext.signIn()
        }

        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val isInternetConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        binding.loginButton.setOnClickListener {
            val email = binding.loginEmail.text.toString()
            val password = binding.loginPassword.text.toString()

            authActivityContext.hideKeyboard()

            if (email == "" || password == ""){
                Log.d(authActivityContext.getTag(), "Empty field(s)!")
                Toast.makeText(activity, "Empty field(s)!", Toast.LENGTH_SHORT).show()

                return@setOnClickListener
            }

            requireActivity().let { activity ->
                FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(activity) { task ->
                        if (task.isSuccessful) {
                            val user = FirebaseAuth.getInstance().currentUser

                            if (user != null) {
                                authActivityContext.getDB().collection("users")
                                    .document(user.uid)
                                    .get()
                                    .addOnSuccessListener {document ->
                                        if (document != null && document.exists()) {
                                            val date = User(user.uid,
                                                "" + document.getString("email"),
                                                "" + document.getString("alias"),
                                                document.get("starred_files") as? List<String> ?: listOf()
                                            )

                                            Log.d(tag, user.toString())

                                            //val intent = Intent(activity, MainActivity::class.java)
                                            //intent.putExtra("user", date)
                                            //startActivity(intent)
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(authActivityContext.getErrTag(), "Error fetching documents: ${e.message}")
                                        Toast.makeText(activity, "Couldn't log in", Toast.LENGTH_SHORT).show()
                                    }

                            } else {
                                Log.e(authActivityContext.getErrTag(), "User does not exist: ${task.exception}")
                                Toast.makeText(activity, "Couldn't log in", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            if (!isInternetConnected) {
                                Log.e(authActivityContext.getErrTag(), "Missing network connection: ${task.exception}")
                                Toast.makeText(activity, "You are not connected to an internet connection", Toast.LENGTH_LONG).show()
                            } else {
                                try {
                                    throw task.exception!!
                                } catch (e: FirebaseAuthInvalidUserException) {
                                    Log.e(authActivityContext.getErrTag(), "Account does not exist: ${e.message}")
                                    Toast.makeText(activity, "This account does not exist!", Toast.LENGTH_SHORT).show()
                                } catch (e: FirebaseAuthInvalidCredentialsException) {
                                    Log.e(authActivityContext.getErrTag(), "Password is wrong: ${e.message}")
                                    Toast.makeText(activity, "Password is wrong!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Log.e(authActivityContext.getErrTag(), "Error logging in: ${e.message}")
                                    Toast.makeText(activity, "Couldn't log in!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
            }
        }

        binding.LoginToRegister.setOnClickListener{
            findNavController().navigate(R.id.action_Login_to_Register)
        }

        binding.LoginToReset.setOnClickListener{
            findNavController().navigate(R.id.action_Login_to_ForgotPassword)
        }

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val layoutParams = binding.loginTitle.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.topMargin = 100

            binding.loginTitle.layoutParams = layoutParams
        }
        else if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT){
            val layoutParams = binding.loginTitle.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.topMargin = 250

            binding.loginTitle.layoutParams = layoutParams
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}