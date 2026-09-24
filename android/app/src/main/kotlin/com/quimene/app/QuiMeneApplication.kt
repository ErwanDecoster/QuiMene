package com.quimene.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.quimene.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

/** [DefaultLifecycleObserver] sur le cycle de vie du *processus* (`ProcessLifecycleOwner`,
 * `androidx.lifecycle:lifecycle-process`) plutôt que d'une `Activity` — équivalent Android de
 * `.onChange(of: scenePhase)` côté SwiftUI, doc 09/14 : `MatchConnectionCoordinator`/
 * `SharedProfileSyncCoordinator` doivent réagir à *l'app* qui revient au premier plan, pas à une
 * `Activity` précise qui pourrait être recréée entre-temps (rotation, etc.). */
class QuiMeneApplication :
    Application(),
    DefaultLifecycleObserver {
    private val applicationScope = CoroutineScope(SupervisorJob())

    lateinit var container: AppContainer
        private set

    // Doc utilisateur — remontée : changer la langue par app (Réglages > Qui Mène ? > Langue)
    // n'avait aucun effet visible. `GameDefinition.LocalizedText.localized` (`:domain`, module
    // JVM pur sans dépendance Android — ADR-0002) lit `Locale.getDefault()`, jamais la
    // `Configuration` Android — et rien ne synchronisait les deux. Une langue par app change la
    // `Configuration` (observable par Compose via `LocalConfiguration`, donc le reste de l'UI
    // suit), mais PAS le `Locale` par défaut de la JVM tant qu'on ne le fait pas explicitement soi-
    // même (contrairement à `AppCompatDelegate`, absent de ce projet — ADR-0012, zéro dépendance
    // tierce évitable). `attachBaseContext` couvre le redémarrage de processus (cas le plus
    // courant après un changement de langue par app) ; `onConfigurationChanged` couvre le cas où
    // le système ne recrée que les `Activity` sans tuer le processus.
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        syncDefaultLocale(base.resources.configuration)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        syncDefaultLocale(newConfig)
    }

    private fun syncDefaultLocale(configuration: Configuration) {
        val locale = configuration.locales.get(0) ?: return
        if (Locale.getDefault() != locale) Locale.setDefault(locale)
    }

    override fun onCreate() {
        super<Application>.onCreate()
        container = AppContainer(this, applicationScope)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /** Lancement (première fois) et chaque retour au premier plan — même déclencheur pour les
     * deux, pas de minuteur propre (doc 09/14). */
    override fun onStart(owner: LifecycleOwner) {
        applicationScope.launch { container.sharedProfileSyncCoordinator.sync() }
        // Doc 16 — reprend la session ouverte par ce créateur, puis rattrape les journaux.
        applicationScope.launch {
            runCatching {
                container.liveShareCoordinator.resumeIfNeeded()
                container.liveShareCoordinator.onForeground()
            }
        }
        applicationScope.launch { runCatching { container.matchConnectionCoordinator.onForeground() } }
    }

    override fun onTerminate() {
        applicationScope.cancel()
        super.onTerminate()
    }
}
