package com.example.skysave.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.navigation.fragment.findNavController
import com.example.skysave.AuthActivity
import com.example.skysave.MainActivity
import com.example.skysave.R
import com.example.skysave.databinding.FragmentSplashScreenBinding


@SuppressLint("CustomSplashScreen")
class SplashScreen : Fragment() {
    private var _binding: FragmentSplashScreenBinding? = null
    private val binding get() = _binding!!

    private lateinit var authActivityContext: AuthActivity


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSplashScreenBinding.inflate(inflater, container, false)

        authActivityContext = (activity as AuthActivity)

        val motionLayout = binding.SplashScreen
        motionLayout.addTransitionListener(object: MotionLayout.TransitionListener{
            override fun onTransitionStarted(motionLayout: MotionLayout?, startId: Int, endId: Int) {
                if (authActivityContext.getLogout()){
                    findNavController().navigate(R.id.action_SplashScreen_to_Login)
                }
            }

            override fun onTransitionChange(motionLayout: MotionLayout?, startId: Int, endId: Int, progress: Float) {

            }

            override fun onTransitionTrigger(motionLayout: MotionLayout?, triggerId: Int, positive: Boolean, progress: Float) {

            }

            override fun onTransitionCompleted(motionLayout: MotionLayout?, currentId: Int) {
                if (authActivityContext.getUser() != null) {
                    val intent = Intent(activity, MainActivity::class.java)
                    startActivity(intent)
                } else {
                    findNavController().navigate(R.id.action_SplashScreen_to_Login)
                }
            }
        })

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}