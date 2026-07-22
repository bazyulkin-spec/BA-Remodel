package com.baremodel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.baremodel.app.ui.editor.MainScreen
import com.baremodel.app.ui.theme.BARemodelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BARemodelTheme {
                MainScreen()
            }
        }
    }
}
