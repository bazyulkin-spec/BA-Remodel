package com.baremodel.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.baremodel.app.data.CrashGuard
import com.baremodel.app.data.UiPrefs
import com.baremodel.app.ui.editor.Entitlements
import com.baremodel.app.ui.editor.MainScreen
import com.baremodel.app.ui.theme.BARemodelTheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiPrefs.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CrashGuard.install(this)
        Entitlements.init(this)
        UiPrefs.init(this)
        setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density * UiPrefs.scale, base.fontScale),
            ) {
                BARemodelTheme {
                    MainScreen()
                }
            }
        }
    }
}
