package com.example.receiptscanner.ui

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.receiptscanner.scanner.ReceiptScannerViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ReceiptScannerScreen(
    viewModel: ReceiptScannerViewModel,
    onFinishScan: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val ocrRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val analysisExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            ocrRecognizer.close()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (cameraPermission.status.isGranted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener(
                        {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analysis ->
                                    analysis.setAnalyzer(analysisExecutor, ThrottledOcrAnalyzer(
                                        targetFps = 3,
                                        onTextDetected = viewModel::onOcrText,
                                        isActive = { viewModel.uiState.value.isAnalyzing },
                                        recognizer = ocrRecognizer
                                    ))
                                }

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        },
                        ContextCompat.getMainExecutor(ctx)
                    )

                    previewView
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Text("Camera permission required to scan receipts")
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            uiState.confirmationReceipt?.let { receipt ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Receipt detected", style = MaterialTheme.typography.titleMedium)
                        Text("Merchant: ${receipt.merchantName}")
                        Text("Date: ${receipt.purchaseDate}")
                        Text("Total: ${receipt.total}")
                    }
                }
            }

            if (uiState.duplicateBlocked) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        modifier = Modifier.padding(12.dp),
                        text = "Duplicate scan skipped.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = {
                        viewModel.stopAnalyzing()
                        onFinishScan()
                    }
                ) {
                    Text("Finish Scan")
                }
            }
        }
    }
}

private class ThrottledOcrAnalyzer(
    private val targetFps: Int,
    private val onTextDetected: (String) -> Unit,
    private val isActive: () -> Boolean,
    private val recognizer: com.google.mlkit.vision.text.TextRecognizer
) : ImageAnalysis.Analyzer {

    private val minIntervalMs = (1000f / targetFps).toLong()
    @Volatile
    private var lastProcessedAtMs: Long = 0L

    override fun analyze(image: ImageProxy) {
        if (!isActive()) {
            image.close()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastProcessedAtMs < minIntervalMs) {
            image.close()
            return
        }
        lastProcessedAtMs = now

        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                onTextDetected(visionText.text)
            }
            .addOnCompleteListener {
                image.close()
            }
    }
}
