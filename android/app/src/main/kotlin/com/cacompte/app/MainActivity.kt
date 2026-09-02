package com.cacompte.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cacompte.designsystem.theme.CaCompteTheme
import com.cacompte.designsystem.tokens.LocalAppColors

/**
 * Point d'entrée minimal — écran blanc thémé (étape A). Les écrans métier (catalogue, partie,
 * résultats…) sont l'étape E, hors périmètre de cette session.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CaCompteTheme {
                BlankScreen()
            }
        }
    }
}

@Composable
private fun BlankScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LocalAppColors.current.neutralBg,
    ) {}
}
