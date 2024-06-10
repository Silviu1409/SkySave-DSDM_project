@file:Suppress("DEPRECATION")

package com.example.skysave


import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.skysave.databinding.ActivityAuthBinding
import com.example.skysave.datatypes.User
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import io.realm.Realm
import io.realm.RealmList
import java.io.ByteArrayOutputStream


class AuthActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAuthBinding

    private val tag = "test"
    private val errTag = "errTest"

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth
    private lateinit var realm: Realm
    private lateinit var storage: StorageReference

    private var user: FirebaseUser? = null

    private var signedInPreviously = true
    private var logout: Boolean = false

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                val email = account.email.toString()

                auth.fetchSignInMethodsForEmail(email)
                    .addOnCompleteListener { task2 ->
                        if (task2.isSuccessful) {
                            val signInMethods = task2.result?.signInMethods

                            if (signInMethods.isNullOrEmpty()) {
                                Log.d(tag, "Not signed in")
                                signedInPreviously = false
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(errTag, "Failed to get signIn methods: ${e.message}")
                    }

                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.e(errTag, "Failed to log in: ${e.message}")
                Toast.makeText(this, "Failed to log in. Try again!", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this,onBackPressedCallback)

        auth = FirebaseAuth.getInstance()
        realm = Realm.getDefaultInstance()
        storage = Firebase.storage.reference

        createGoogleSignInClient()

        user = auth.currentUser

        val aux = intent.getBooleanExtra("logout", false)

        if (aux){
            logout = true
        }
    }

    fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        launcher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser

                    if (!signedInPreviously){
                        val userData = HashMap<String, Any>()
                        userData["email"] = user?.email.toString()
                        userData["alias"] = user?.displayName.toString()
                        userData["starred_files"] = listOf<String>()

                        if (user != null) {
                            realm.executeTransactionAsync({ realm ->
                                val newUser = realm.createObject(User::class.java, user.uid)
                                newUser.email = userData["email"].toString()
                                newUser.alias = userData["alias"].toString()


                                newUser.starred_files = RealmList()
                            }, {
                                val glide = Glide.with(this)

                                val requestBuilder = glide.asBitmap()
                                    .load(user.photoUrl.toString())
                                    .apply(RequestOptions().override(75, 75))
                                requestBuilder.into(object : CustomTarget<Bitmap>() {
                                    override fun onResourceReady(
                                        resource: Bitmap,
                                        transition: Transition<in Bitmap>?
                                    ) {
                                        val folderRef = storage.child(user.uid)
                                        val imageRef = folderRef.child("icon.jpg")

                                        val baos = ByteArrayOutputStream()
                                        resource.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                                        val data = baos.toByteArray()

                                        val uploadTask = imageRef.putBytes(data)
                                        uploadTask.addOnSuccessListener {
                                            Log.d(tag, "Created folder for user")

                                            val intent = Intent(this@AuthActivity, MainActivity::class.java)
                                            intent.putExtra("userId", user.uid)
                                            startActivity(intent)
                                        }.addOnFailureListener { e ->
                                            Log.e(errTag, "Error creating folder: ${e.message}")
                                            Toast.makeText(this@AuthActivity, "Couldn't create folder", Toast.LENGTH_SHORT).show()
                                        }
                                    }

                                    override fun onLoadCleared(placeholder: Drawable?) {
                                        Log.w(tag, "Cancelled load")
                                    }
                                })
                            }, {
                                Log.e(errTag, "Error saving user data to Realm")
                                Toast.makeText(this, "Couldn't register", Toast.LENGTH_SHORT).show()
                            })
                        } else {
                            Log.e(errTag, "User wasn't created: ${task.exception}")
                            Toast.makeText(this, "Couldn't register", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        if (user != null) {
                            val userData = realm.where(User::class.java).equalTo("uid", user.uid).findFirst()

                            if (userData != null) {
                                val intent = Intent(this, MainActivity::class.java)
                                intent.putExtra("userId", user.uid)
                                startActivity(intent)

                            } else {
                                Log.e(errTag, "User data not found in Realm")
                                Toast.makeText(this, "Couldn't log in", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.e(errTag, "User does not exist: ${task.exception}")
                            Toast.makeText(this, "Couldn't log in", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.e(errTag, "Failed to log in: ${task.exception}")
                    Toast.makeText(this, "Failed to log in!", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun createGoogleSignInClient() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleSignInClient.signOut()
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            exitApp()
        }
    }

    private fun exitApp() {
        MaterialAlertDialogBuilder(this)
            .setMessage("Do you want to close the app?")
            .setPositiveButton("Yes") { _, _ -> finish() }
            .setNegativeButton("No", null)
            .show()
    }

    fun hideKeyboard() {
        if(currentFocus != null) {
            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)
        }
    }

    fun getUser(): FirebaseUser? {
        return user
    }

    fun getLogout(): Boolean {
        return logout
    }

    fun getTag(): String{
        return tag
    }

    fun getErrTag(): String{
        return errTag
    }

    fun getRealm(): Realm {
        return realm
    }

    fun getStorage(): StorageReference{
        return storage
    }
}