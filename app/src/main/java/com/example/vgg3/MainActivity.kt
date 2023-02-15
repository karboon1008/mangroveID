package com.example.vgg3

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.vgg3.ml.*
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity

class WelcomePage : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.welcome)

        Handler().postDelayed({
            val intent = Intent(this@WelcomePage, MainActivity::class.java)
            startActivity(intent)
            finish()
        }, 5000)
    }
}


class MainActivity : AppCompatActivity() {

    lateinit var selectBtn: Button
    lateinit var predictBtn: Button
    lateinit var result: TextView
    lateinit var imageView: ImageView
    lateinit var captureBtn: Button
    lateinit var bitmap: Bitmap

    companion object {
        // Define the pic id
        private const val pic_id = 123
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        selectBtn = findViewById(R.id.selectBtn)
        predictBtn = findViewById(R.id.predictBtn)
        captureBtn = findViewById(R.id.captureBtn)
        imageView = findViewById(R.id.imageView)
        result = findViewById(R.id.result)

        if(ActivityCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA),111)
        }

        // Camera_open button is for open the camera and add the setOnClickListener in this button
        captureBtn.setOnClickListener {
            // Create the camera_intent ACTION_IMAGE_CAPTURE it will open the camera for capture the image
            val camera_intent =
                Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            // Start the activity with camera_intent, and request pic id
            startActivityForResult(camera_intent, pic_id)
        }


        val labels = application.assets.open("labels.txt").bufferedReader().readLines()

        // image processor
        val imageProcessor = ImageProcessor.Builder()
            // Normalize the image
            //.add(NormalizeOp(0.0f,225.0f))
            // Resize image to (224,224)
            .add(ResizeOp(224,224,ResizeOp.ResizeMethod.BILINEAR))
            .build()

        selectBtn.setOnClickListener {
            val intent = Intent()
            intent.setAction(Intent.ACTION_GET_CONTENT)
            intent.setType("image/*")
            startActivityForResult(intent,100)
        }


        predictBtn.setOnClickListener {
            var tensorImage =  TensorImage(DataType.FLOAT32)
            // pass bitmap
            tensorImage.load(bitmap)

            tensorImage = imageProcessor.process(tensorImage)

            val background = detectBackground(bitmap)
            if (background == "This image has a white background") {

                val model = L1EarlystoppingOptimizedVgg16White8020KfoldConverted.newInstance(this)

                // Creates inputs for reference.
                val inputFeature0 =
                    TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
                inputFeature0.loadBuffer(tensorImage.buffer)

                // Runs model inference and gets result.
                val outputs = model.process(inputFeature0)
                val outputFeature0 = outputs.outputFeature0AsTensorBuffer.floatArray

                var maxIdx = 0
                outputFeature0.forEachIndexed { index, fl ->
                    if (outputFeature0[maxIdx] < fl) {
                        maxIdx = index
                    }
                }

                result.setText(labels[maxIdx])

                // Releases model resources if no longer used.
                model.close()

            }
            if (background == "This image has a complex background") {

                val model = VggOptimzedComplexStrat8020Converted.newInstance(this)

                // Creates inputs for reference.
                val inputFeature0 =
                    TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
                inputFeature0.loadBuffer(tensorImage.buffer)

                // Runs model inference and gets result.
                val outputs = model.process(inputFeature0)
                val outputFeature0 = outputs.outputFeature0AsTensorBuffer.floatArray

                var maxIdx = 0
                outputFeature0.forEachIndexed { index, fl ->
                    if (outputFeature0[maxIdx] < fl) {
                        maxIdx = index
                    }
                }

                result.setText(labels[maxIdx])

                // Releases model resources if no longer used.
                model.close()
            }
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Match the request 'pic id with requestCode
        if (requestCode == pic_id && resultCode == RESULT_OK) {
            // BitMap is data structure of image file which store the image in memory
            bitmap = data?.extras?.get("data") as Bitmap
            imageView.setImageBitmap(bitmap)
        }

        if (requestCode == 100) {
            val uri = data?.data;
            bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
            imageView.setImageBitmap(bitmap)
        }
    }

    fun detectBackground(image: Bitmap): String {
        // Convert the image to grayscale
        val width = image.width
        val height = image.height
        val grayImage = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val color = image.getPixel(x, y)
                val gray = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3
                grayImage.setPixel(x, y, Color.rgb(gray, gray, gray))
            }
        }

        // Apply a threshold to the grayscale image
        val threshold = 128
        val thresholdedImage = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val color = grayImage.getPixel(x, y)
                val gray = Color.red(color)
                if (gray > threshold) {
                    thresholdedImage.setPixel(x, y, Color.WHITE)
                } else {
                    thresholdedImage.setPixel(x, y, Color.BLACK)
                }
            }
        }

        // Count the number of white pixels in the thresholded image
        var whitePixelCount = 0
        for (x in 0 until width) {
            for (y in 0 until height) {
                val color = thresholdedImage.getPixel(x, y)
                if (color == Color.WHITE) {
                    whitePixelCount++
                }
            }
        }
        // Decide whether the background is white or complex
        val whitePixelPercentage = whitePixelCount.toFloat() / (width * height)
        val whiteBackgroundThreshold = 0.7f
        return if (whitePixelPercentage > whiteBackgroundThreshold) {
            Toast.makeText(this, "This image has a white background", Toast.LENGTH_SHORT).show()
            "This image has a white background"
        } else {
            Toast.makeText(this, "This image has a complex background", Toast.LENGTH_SHORT).show()
            "This image has a complex background"
        }
    }
}
