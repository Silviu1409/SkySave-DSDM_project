package com.example.skysave.auth

import android.content.Context
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
import com.example.skysave.AuthActivity
import com.example.skysave.R
import com.example.skysave.databinding.FragmentForgotPasswordBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException


class ForgotPassword : Fragment() {
    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = _binding!!

    private lateinit var authActivityContext: AuthActivity


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)

        authActivityContext = (activity as AuthActivity)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val isInternetConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true


        binding.resetButton.setOnClickListener {
            val email = binding.resetEmail.text.toString()

            authActivityContext.hideKeyboard()

            if (email == "" ){
                Log.d(authActivityContext.getTag(), "No email address provided!")
                Toast.makeText(activity, "No email address provided!", Toast.LENGTH_SHORT).show()

                return@setOnClickListener
            }

            requireActivity().let { activity ->
                FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                    .addOnCompleteListener(activity) { task ->
                        if (task.isSuccessful) {
                            binding.resetEmail.text?.clear()

                            Log.d(authActivityContext.getTag(), "Link sent on email!")
                            Toast.makeText(activity, "Link sent on your email!", Toast.LENGTH_SHORT).show()
                        }
                        else {
                            if (!isInternetConnected) {
                                Log.e(authActivityContext.getErrTag(), "Missing network connection: ${task.exception}")
                                Toast.makeText(activity, "You are not connected to an internet connection", Toast.LENGTH_LONG).show()
                            } else {
                                try {
                                    throw task.exception!!
                                } catch (e: FirebaseAuthInvalidUserException) {
                                    Log.e(authActivityContext.getErrTag(), "Email is not valid: ${e.message}")
                                    Toast.makeText(activity, "Email is not valid!", Toast.LENGTH_SHORT).show()
                                } catch (e: FirebaseAuthInvalidCredentialsException) {
                                    Log.e(authActivityContext.getErrTag(), "Wrong email format: ${e.message}")
                                    Toast.makeText(activity, "Wrong email format!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Log.e(authActivityContext.getErrTag(), "Couldn't reset password: ${e.message}")
                                    Toast.makeText(activity, "Couldn't reset password!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
            }
        }

        binding.ResetToAuth.setOnClickListener{
            findNavController().navigate(R.id.action_ForgotPassword_to_Register)
        }

        binding.ResetToLogin.setOnClickListener{
            findNavController().navigate(R.id.action_ForgotPassword_to_Login)
        }

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ) {
            val layoutParams = binding.resetTitle.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.topMargin = 100

            binding.resetTitle.layoutParams = layoutParams
        }
        else if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT){
            val layoutParams = binding.resetTitle.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.topMargin = 350

            binding.resetTitle.layoutParams = layoutParams
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}