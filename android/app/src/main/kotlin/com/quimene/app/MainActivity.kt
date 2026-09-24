package com.quimene.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.quimene.app.di.LocalAppContainer
import com.quimene.app.navigation.QuiMeneApp
import com.quimene.designsystem.theme.QuiMeneTheme

/** Point d'entrée — fournit [LocalAppContainer] (`AppContainer` construit par
 * [QuiMeneApplication]) puis délègue tout le reste à [QuiMeneApp] (thème + navigation). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as QuiMeneApplication).container
        setContent {
            QuiMeneTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    QuiMeneApp()
                }
            }
        }
    }
}
