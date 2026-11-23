package com.devlink42.cinepathe.data.local

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

fun generateQrBitmap(content: String, sizePx: Int = 512): ImageBitmap {
    val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    return bmp.asImageBitmap()
}

fun generateBarcodeBitmap(content: String, widthPx: Int = 800, heightPx: Int = 220): ImageBitmap {
    val matrix = MultiFormatWriter().encode(content, BarcodeFormat.CODE_128, widthPx, heightPx)
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.RGB_565)
    for (x in 0 until widthPx) {
        for (y in 0 until heightPx) {
            bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    return bmp.asImageBitmap()
}
