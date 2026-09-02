package com.cacompte.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.cacompte.app.di.LocalAppContainer
import com.cacompte.app.navigation.CaCompteApp
import com.cacompte.designsystem.theme.CaCompteTheme

/** Point d'entrée — fournit [LocalAppContainer] (`AppContainer` construit par
 * [CaCompteApplication]) puis délègue tout le reste à [CaCompteApp] (thème + navigation). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CaCompteApplication).container
        setContent {
            CaCompteTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    CaCompteApp()
                }
            }
        }
    }
}
