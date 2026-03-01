package com.wirewhisper.ui.util

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

@Composable
fun rememberDrawableBitmap(drawable: Drawable?, size: Int = 64): ImageBitmap? {
    return remember(drawable, size) {
        drawable?.toBitmap(size, size)?.asImageBitmap()
    }
}
