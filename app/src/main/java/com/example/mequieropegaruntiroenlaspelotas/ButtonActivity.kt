package com.example.mequieropegaruntiroenlaspelotas

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

class ButtonActivity : ComponentActivity() {
    private val MAX_BLINK_INTERVAL = 1000L // 1 segundo
    private var isNavigating = false // Variable para evitar múltiples navegaciones

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val buttonText = intent.getStringExtra("button_text") ?: "No Text"

        setContent {
            val context = LocalContext.current
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val executor = Executors.newSingleThreadExecutor()
            var selectedButtonIndex by remember { mutableStateOf(0) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text(text = buttonText)

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { /* Acción del botón de volver */ },
                    border = if (selectedButtonIndex == 0) BorderStroke(2.dp, Color.Blue) else null,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "Volver")
                }
            }

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val options = FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .build()

                val detector = FaceDetection.getClient(options)

                val imageAnalyzer = ImageAnalysis.Builder()
                    .build()
                    .also {
                        it.setAnalyzer(executor, { imageProxy ->
                            processImageProxy(detector, imageProxy) { blinkDetected, smileDetected ->
                                if (blinkDetected) {
                                    selectedButtonIndex = (selectedButtonIndex + 1) % 1
                                }
                                if (smileDetected && !isNavigating) {
                                    isNavigating = true
                                    val intent = Intent(this, MainActivity::class.java)
                                    startActivity(intent)
                                }
                            }
                        })
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        this, cameraSelector, imageAnalyzer
                    )
                } catch (exc: Exception) {
                    Log.e("CameraXApp", "Error al iniciar la cámara", exc)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(
        detector: com.google.mlkit.vision.face.FaceDetector,
        imageProxy: ImageProxy,
        onGestureDetected: (Boolean, Boolean) -> Unit
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    var blinkDetected = false
                    var smileDetected = false
                    for (face in faces) {
                        face.smilingProbability?.let { smileProb ->
                            if (smileProb > 0.5) {
                                Log.e("FaceDetection", "¡Sonrisa detectada!")
                                smileDetected = true
                            }
                        }
                        face.leftEyeOpenProbability?.let { leftEyeProb ->
                            face.rightEyeOpenProbability?.let { rightEyeProb ->
                                if (leftEyeProb < 0.5 && rightEyeProb < 0.5) {
                                    Log.e("FaceDetection", "¡Parpadeo detectado!")
                                    blinkDetected = true
                                }
                            }
                        }
                    }
                    onGestureDetected(blinkDetected, smileDetected)
                }
                .addOnFailureListener { e ->
                    Log.e("FaceDetection", "Error al detectar la cara", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }
}