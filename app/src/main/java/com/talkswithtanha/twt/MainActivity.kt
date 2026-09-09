package com.talkswithtanha.twt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.talkswithtanha.twt.core.navigation.AppNavigation
import com.talkswithtanha.twt.core.theme.TWTForexTheme

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