package com.cacompte.app.di

import androidx.compose.runtime.staticCompositionLocalOf

/** Fourni à la racine de la composition par [com.cacompte.app.MainActivity] — chaque écran y
 * accède pour construire son ViewModel via `viewModelFactory { initializer { ... } }`, seule
 * façon idiomatique d'injecter des dépendances sans Hilt/Dagger (ADR-0012). */
val LocalAppContainer =
    staticCompositionLocalOf<AppContainer> {
        error("LocalAppContainer n'est fourni qu'à l'intérieur de CaCompteTheme/CaCompteApp.")
    }
