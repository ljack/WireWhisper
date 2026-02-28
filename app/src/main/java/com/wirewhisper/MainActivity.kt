package com.wirewhisper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wirewhisper.ui.navigation.WireWhisperNavHost
import com.wirewhisper.ui.theme.WireWhisperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WireWhisperTheme {
                WireWhisperNavHost()
            }
        }
    }
}
