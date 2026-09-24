package com.quimene.app.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Construit un ViewModel avec des dépendances (`AppContainer`, arguments de route…) sans
 * Hilt/Dagger (ADR-0012) — évite de répéter `viewModelFactory { initializer { ... } } ` à chaque
 * écran. */
@Composable
inline fun <reified VM : ViewModel> rememberViewModel(crossinline creator: () -> VM): VM =
    viewModel(factory = viewModelFactory { initializer { creator() } })
