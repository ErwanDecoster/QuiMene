package com.cacompte.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.cacompte.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** [DefaultLifecycleObserver] sur le cycle de vie du *processus* (`ProcessLifecycleOwner`,
 * `androidx.lifecycle:lifecycle-process`) plutôt que d'une `Activity` — équivalent Android de
 * `.onChange(of: scenePhase)` côté SwiftUI, doc 09/14 : `MatchConnectionCoordinator`/
 * `SharedProfileSyncCoordinator` doivent réagir à *l'app* qui revient au premier plan, pas à une
 * `Activity` précise qui pourrait être recréée entre-temps (rotation, etc.). */
class CaCompteApplication :
    Application(),
    DefaultLifecycleObserver {
    private val applicationScope = CoroutineScope(SupervisorJob())

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super<Application>.onCreate()
        container = AppContainer(this, applicationScope)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /** Lancement (première fois) et chaque retour au premier plan — même déclencheur pour les
     * deux, pas de minuteur propre (doc 09/14). */
    override fun onStart(owner: LifecycleOwner) {
        applicationScope.launch { container.sharedProfileSyncCoordinator.sync() }
    }

    override fun onTerminate() {
        applicationScope.cancel()
        super.onTerminate()
    }
}
