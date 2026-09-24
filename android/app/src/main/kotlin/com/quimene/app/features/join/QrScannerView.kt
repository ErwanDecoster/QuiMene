package com.quimene.app.features.join

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.quimene.designsystem.components.PrimaryButton
import com.quimene.designsystem.tokens.LocalAppColors
import com.quimene.designsystem.tokens.Space
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Aperçu caméra intégré à l'écran, scan continu de QR — miroir de `QRScannerView.swift`
 * (`AVCaptureMetadataOutput`). CameraX + ML Kit plutôt que
 * `com.google.mlkit.vision.codescanner.GmsBarcodeScanning` (Play Services Code Scanner) : ce
 * dernier affiche son propre écran opaque, système, sans moyen d'y superposer un bouton Compose
 * — incompatible avec le bouton flottant « Saisir un code » demandé par-dessus l'aperçu (doc
 * utilisateur). [onScan] peut être appelé plusieurs fois tant que l'appelant ne quitte pas la
 * composition (chaque image analysée qui contient un QR déclenche un appel) — à l'appelant de
 * n'agir qu'une fois (ex. un drapeau `hasScanned`).
 */
@Composable
fun QrScannerView(
    onScan: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasPermission = granted
        }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (hasPermission) {
        CameraPreview(onScan = onScan, modifier = modifier)
    } else {
        CameraPermissionRationale(
            modifier = modifier,
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        )
    }
}

@OptIn(markerClass = [ExperimentalGetImage::class])
@Composable
private fun CameraPreview(
    onScan: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnScan by rememberUpdatedState(onScan)
    val scanner =
        remember {
            BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
            )
        }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            scanner.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis =
                    ImageAnalysis
                        .Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                    analyzeFrame(scanner, imageProxy, currentOnScan)
                }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}

/** Doc utilisateur — `imageProxy.close()` dans TOUS les chemins (succès, échec, image nulle) :
 * CameraX ne fournit l'image suivante qu'une fois la précédente refermée
 * (`STRATEGY_KEEP_ONLY_LATEST`) — l'oublier gèle l'aperçu après la première image. */
@OptIn(markerClass = [ExperimentalGetImage::class])
private fun analyzeFrame(
    scanner: BarcodeScanner,
    imageProxy: ImageProxy,
    onScan: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner
        .process(image)
        .addOnSuccessListener { barcodes ->
            barcodes.firstNotNullOfOrNull { it.rawValue }?.let(onScan)
        }.addOnCompleteListener {
            imageProxy.close()
        }
}

@Composable
private fun CameraPermissionRationale(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "L'accès à l'appareil photo est nécessaire pour scanner un code QR — tu peux aussi saisir le code à la main ci-dessous.",
            style = MaterialTheme.typography.bodyLarge,
            color = LocalAppColors.current.textSecondary,
        )
        PrimaryButton(
            text = "Autoriser l'appareil photo",
            onClick = onRequestPermission,
            modifier = Modifier.padding(top = Space.lg),
        )
    }
}
