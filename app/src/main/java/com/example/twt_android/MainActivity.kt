package com.example.twt_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.twt_android.core.navigation.AppNavigation
import com.example.twt_android.core.theme.TWTForexTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TWTForexTheme {
                AppNavigation()
            }
        }
    }
}