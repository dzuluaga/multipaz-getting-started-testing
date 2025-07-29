package org.example.project

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun decodeImageFromBytes(bytes: ByteArray): ImageBitmap? {
    return try {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        bitmap?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
} 