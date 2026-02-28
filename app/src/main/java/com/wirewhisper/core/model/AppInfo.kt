package com.wirewhisper.core.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val uid: Int,
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
)
