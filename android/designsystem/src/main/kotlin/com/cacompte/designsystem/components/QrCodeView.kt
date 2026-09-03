package com.cacompte.designsystem.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Miroir de `QRCodeView.swift` — encode [content] en QR (ZXing, seule brique manquant côté
 * Android : contrairement à iOS, aucune API système n'encode un QR, seulement
 * `play-services-code-scanner` pour le lecteur). Rendu dans une résolution fixe assez grande
 * (512 px) pour rester net une fois agrandi par [Modifier.size] côté appelant — même raison que
 * le facteur d'agrandissement x10 d'`QRCodeView.swift` avant rasterisation.
 */
@Composable
fun QrCodeView(
    content: String,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(content) { generateQrBitmap(content) } ?: return
    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Code QR d'appairage", modifier = modifier)
}

private const val QR_RESOLUTION_PX = 512

private fun generateQrBitmap(
    content: String,
    sizePx: Int = QR_RESOLUTION_PX,
): Bitmap? =
    runCatching {
        val matrix =
            QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
            )
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) BLACK else WHITE)
            }
        }
        bitmap
    }.getOrNull()

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
