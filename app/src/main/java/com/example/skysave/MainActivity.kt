package com.example.skysave

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.skysave.databinding.ActivityMainBinding
import com.example.skysave.datatypes.User
import com.example.skysave.main.Files
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.log10
import kotlin.math.pow


@Suppress("BlockingMethodInNonBlockingContext")
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val tag = "test"
    private val errTag = "errtest"

    private lateinit var auth: FirebaseAuth
    private lateinit var folderRef: StorageReference
    private lateinit var db: FirebaseFirestore

    private var user: User? = null

    private lateinit var selectedFileUri: Uri
    private lateinit var fileName: String

    private val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    private val dirName = "SkySave"
    private val appDir = File(downloadsDir, dirName)
    private lateinit var fileDir: File

    private var folderSize = 0L
    private var fileSize = 0L
    private var folderSizeLimit = 1024 * 1024 * 1024L    // 1 GB file storage limit
    private var fileSizeLimit = 50 * 1024 * 1024L    // 50 MB upload file limit size

    private lateinit var sharedPreferencesStorage: SharedPreferences
    private lateinit var sharedPreferencesUser: SharedPreferences
    private var uri: Uri? = null


    private val getDocumentContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                fileSize = this.contentResolver.openInputStream(uri)?.available()?.toLong()?: 0L

                if (fileSize > fileSizeLimit){
                    Log.e(tag, "File is too large (> 50MB)")
                    Toast.makeText(this, "File is too large (> 50MB)", Toast.LENGTH_SHORT).show()
                }
                else {
                    selectedFileUri = uri

                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        cursor.moveToFirst()
                        fileName = cursor.getString(nameIndex)
                    }

                    uploadFile()
                }
            }
        }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            exitApp()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferencesStorage = getSharedPreferences("storage_space", Context.MODE_PRIVATE)
        sharedPreferencesUser = getSharedPreferences("user", Context.MODE_PRIVATE)

        val navView: BottomNavigationView = binding.navMenu

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.activity_main) as NavHostFragment
        val navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_files, R.id.nav_trash, R.id.nav_profile
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        navView.menu.findItem(R.id.add_placeholder).isEnabled = false

        supportActionBar?.hide()

        onBackPressedDispatcher.addCallback(this,onBackPressedCallback)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        uri = intent.data
        if (uri != null) {
            Log.d(tag, "Opened from uri (notification)!")
        }

        if (sharedPreferencesUser.contains("uid")) {
            user = User(
                sharedPreferencesUser.getString("uid", "")!!,
                sharedPreferencesUser.getString("email", "")!!,
                sharedPreferencesUser.getString("alias", "")!!,
                sharedPreferencesUser.getStringSet("starred_files", setOf<String>())?.toList()!!
            )
        } else {
            @Suppress("DEPRECATION")
            user = intent.getSerializableExtra("user") as? User

            if (user == null){
                val intent = Intent(this, AuthActivity::class.java)
                intent.putExtra("logout", true)
                startActivity(intent)
            } else {
                sharedPreferencesUser.edit().putString("uid", user!!.uid).apply()
                sharedPreferencesUser.edit().putString("alias", user!!.alias).apply()
                sharedPreferencesUser.edit().putString("email", user!!.email).apply()
                sharedPreferencesUser.edit().putStringSet("starred_files", HashSet(user!!.starred_files)).apply()
            }
        }

        folderRef = Firebase.storage.reference.child(user!!.uid)
        folderSize = sharedPreferencesStorage.getLong(user!!.uid, 0L)
        setStorageSpaceUsed(getReadableFileSize(folderSize.toDouble()))

        if (folderSize == 0L) {
            // get space used in folder
            lifecycleScope.launch(Dispatchers.Main) {
                folderSize = getFolderSize(folderRef)
                changePreferencesFolderSize()
                setStorageSpaceUsed(getReadableFileSize(folderSize.toDouble()))

                Log.d(tag, "Got folder size: $folderSize bytes")
            }
        }

        binding.uploadFab.setOnClickListener {
            getDocumentContent.launch("*/*")
        }

        if (!appDir.exists()) {
            appDir.mkdir()
        }

        fileDir = appDir.resolve(user!!.alias)

        if (!fileDir.exists()) {
            fileDir.mkdir()
        }

        onConfigurationChanged(resources.configuration)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val cardView = binding.navCard

        cardView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val cardWidth = cardView.width

                val layoutParams = binding.uploadFab.layoutParams as ViewGroup.MarginLayoutParams
                layoutParams.marginStart = (0.125 * cardWidth).toInt()

                binding.uploadFab.layoutParams = layoutParams

                cardView.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }

    private fun uploadFile() {
        // check if firebase storage cap has been reached
        if (folderSize + fileSize > folderSizeLimit) {
            Log.e(tag, "Cannot upload file, storage limit reached")

            Toast.makeText(this, "Cannot upload, storage limit reached!", Toast.LENGTH_SHORT).show()

            return
        }

        notificationUploadChannel("upload_notification_channel", NotificationManager.IMPORTANCE_LOW)

        val fileRef = folderRef.child("files/$fileName")

        fileRef.downloadUrl
            .addOnSuccessListener {
                Log.w(tag, "File already exists!")
                Toast.makeText(this, "File already uploaded!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Log.d(tag, "File doesn't already exist.")

                val uploadTask = fileRef.putFile(selectedFileUri)

                val notificationId = 0

                val notificationBuilder = NotificationCompat.Builder(this, "upload_notification_channel")
                    .setContentTitle("Uploading $fileName")
                    .setSmallIcon(R.drawable.icon_notification)
                    .setProgress(100, 0, false)
                    .setPriority(NotificationCompat.PRIORITY_LOW)

                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.notify(notificationId, notificationBuilder.build())

                uploadTask
                    .addOnProgressListener { snapshot ->
                        val progress = (100.0 * snapshot.bytesTransferred / snapshot.totalByteCount).toInt()
                        notificationBuilder.setProgress(100, progress, false).setContentText("$progress%")
                        notificationManager.notify(notificationId, notificationBuilder.build())
                    }
                    .addOnSuccessListener { snapshot ->
                        notificationBuilder.setContentText("Upload complete")
                            .setProgress(0, 0, false)
                        notificationManager.notify(notificationId, notificationBuilder.build())

                        notificationUploadChannel("upload_notification_channel_2", NotificationManager.IMPORTANCE_HIGH)

                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://skysave.com/files"), applicationContext, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                        val pendingIntent = PendingIntent.getActivity(applicationContext, 1, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)

                        val completedNotificationBuilder = NotificationCompat.Builder(applicationContext, "upload_notification_channel_2")
                            .setSmallIcon(R.drawable.icon_notification)
                            .setContentTitle("File Upload Complete")
                            .setContentText("Your file has been uploaded successfully.")
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                        notificationManager.notify(notificationId, completedNotificationBuilder.build())

                        val fragment = supportFragmentManager.fragments.first()
                        val wantedFragment = fragment.childFragmentManager.fragments.first()

                        if (wantedFragment is Files) {
                            val newFile = snapshot.metadata?.reference

                            if (newFile != null) {
                                wantedFragment.refreshRecyclerView(newFile)
                            } else {
                                Log.w(tag, "RecycleView not updated.")
                            }
                        }

                        folderSize += fileSize
                        changePreferencesFolderSize()
                        setStorageSpaceUsed(getReadableFileSize(folderSize.toDouble()))

                        Log.d(tag, "File uploaded")
                    }
                    .addOnFailureListener { e ->
                        notificationBuilder
                            .setContentText("Upload failed: ${e.message}")
                            .setProgress(0, 0, false)

                        notificationManager.notify(notificationId, notificationBuilder.build())

                        Log.e(tag, "Failed to upload file: ${e.message}")
                    }
            }
    }

    private fun notificationUploadChannel(channelId: String, importance: Int) {
        val channelName = "Upload Notification Channel"
        val channelDescription = "Shows upload progress while a file is uploaded to Firebase Storage"
        val channel = NotificationChannel(channelId, channelName, importance)
        channel.description = channelDescription
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun exitApp() {
        MaterialAlertDialogBuilder(this)
            .setMessage("Do you want to close the app?")
            .setPositiveButton("Yes") { _, _ -> finishAffinity() }
            .setNegativeButton("No", null)
            .show()
    }

    private suspend fun getFolderSize(storageRef: StorageReference): Long = withContext(Dispatchers.IO) {
        var size = 0L
        val prefixes = listOf("files/", "trash/")

        for (prefix in prefixes) {
            val prefixRef = storageRef.child(prefix)
            val listResult = prefixRef.listAll().await()

            for(item in listResult.items){
                val metadata = item.metadata.await()

                size += metadata.sizeBytes
            }
        }

        return@withContext size
    }

    fun getReadableFileSize(size: Double): String {
        if (size <= 0) {
            return "0 bytes"
        }

        val units = arrayOf("bytes", "KB", "MB")
        val digitGroups = (log10(size) / log10(1024.0)).toInt()
        val sizeFormatted = String.format("%.2f", size / 1024.0.pow(digitGroups.toDouble()))
        Log.d("i", sizeFormatted)
        //val sizeRounded = BigDecimal(sizeFormatted).setScale(2, RoundingMode.HALF_UP).toDouble()

        return String.format("%s %s", sizeFormatted, units[digitGroups])
    }

    fun getAuth(): FirebaseAuth{
        return auth
    }

    fun getUser(): User?{
        return user
    }

    fun getFolderRef(): StorageReference{
        return folderRef
    }

    fun hideKeyboard() {
        if(currentFocus != null) {
            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)
        }
    }

    fun getDb(): FirebaseFirestore {
        return db
    }

    fun getTag(): String{
        return tag
    }

    fun getErrTag(): String{
        return errTag
    }

    fun getFileDir(): File{
        return fileDir
    }

    fun setFileDir(newDir: File){
        fileDir = newDir
    }

    fun getFolderSize(): Long{
        return folderSize
    }

    fun changeFolderSize(fileSize: Long){
        folderSize += fileSize
    }

    fun setStorageFabVisibility(visibility: Int){
        binding.storageSpace.visibility = visibility
    }

    fun setStorageSpaceUsed(spaceUsed: String){
        binding.usedSpace.text = spaceUsed
    }

    fun changePreferencesFolderSize(){
        sharedPreferencesStorage.edit().putLong(user!!.uid, folderSize).apply()
    }

    fun removePreferencesUser(){
        sharedPreferencesUser.edit().clear().apply()
    }

    fun getSharedPreferencesUser(): SharedPreferences {
        return sharedPreferencesUser
    }
}