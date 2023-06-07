package com.example.aplikasi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var tvPredictedName: TextView

    private val CAMERA_PERMISSION_REQUEST_CODE = 101
    private val GALLERY_REQUEST_CODE = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        tvPredictedName = findViewById(R.id.tvPredictedName)

        val btnChooseImage: Button = findViewById(R.id.btnChooseImage)
        btnChooseImage.setOnClickListener {
            chooseImageFromGallery()
        }

        val btnCaptureImage: Button = findViewById(R.id.btnCaptureImage)
        btnCaptureImage.setOnClickListener {
            captureImage()
        }

        val btnUploadImage: Button = findViewById(R.id.btnUploadImage)
        btnUploadImage.setOnClickListener {
            uploadImage()
        }
    }

    private fun chooseImageFromGallery() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            openGallery()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                GALLERY_REQUEST_CODE
            )
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, GALLERY_REQUEST_CODE)
    }

    private fun captureImage() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCameraActivity()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun startCameraActivity() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, CAMERA_PERMISSION_REQUEST_CODE)
    }

    private fun uploadImage() {
        val bitmap = imageView.drawable?.toBitmap()
        if (bitmap != null) {
            val base64Image = bitmapToBase64(bitmap)
            val apiEndpoint = "https://f28d-35-230-63-174.ngrok-free.app/predict"
            val apiKey = "image"

            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val url = URL(apiEndpoint)
                    val connection = url.openConnection() as HttpsURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Accept", "application/json")

                    val data = JSONObject()
                    data.put(apiKey, base64Image)

                    val outputStream = connection.outputStream
                    outputStream.write(data.toString().toByteArray())
                    outputStream.close()

                    val responseCode = connection.responseCode
                    if (responseCode == HttpsURLConnection.HTTP_OK) {
                        val responseData = connection.inputStream.bufferedReader().use { it.readText() }
                        val jsonResponse = JSONObject(responseData)
                        val predictedName = jsonResponse.getString("predicted_name")

                        launch(Dispatchers.Main) {
                            showResult(predictedName)
                        }
                    } else {
                        launch(Dispatchers.Main) {
                            showResult("Error: $responseCode")
                        }
                    }

                    connection.disconnect()
                } catch (e: Exception) {
                    e.printStackTrace()
                    launch(Dispatchers.Main) {
                        showResult("Error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val imageBytes = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.DEFAULT)
    }

    private fun showResult(predictedName: String) {
        tvPredictedName.text = "Hasil Prediksi: $predictedName"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            GALLERY_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openGallery()
                } else {
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                }
            }
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startCameraActivity()
                } else {
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            GALLERY_REQUEST_CODE -> {
                if (resultCode == RESULT_OK && data != null) {
                    val selectedImage: Uri? = data.data
                    try {
                        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, selectedImage)
                        imageView.setImageBitmap(bitmap)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (resultCode == RESULT_OK && data != null) {
                    val bitmap = data.extras?.get("data") as Bitmap
                    imageView.setImageBitmap(bitmap)
                }
            }
        }
    }
}
