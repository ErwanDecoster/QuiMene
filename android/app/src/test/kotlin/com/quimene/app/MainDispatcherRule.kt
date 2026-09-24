package com.quimene.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * `viewModelScope` est câblé sur `Dispatchers.Main.immediate` — sans cette règle, le travail
 * lancé dans le `init` d'un ViewModel (ex. [com.quimene.app.features.matchsetup.MatchSetupViewModel])
 * ne s'exécute jamais en test JVM pur. [UnconfinedTestDispatcher] fait tourner les coroutines
 * de façon immédiate/synchrone — combiné à `RoomTestBase` qui force aussi les exécuteurs Room sur
 * le thread appelant, tout le chargement d'un ViewModel se termine avant la fin de son
 * constructeur, sans avoir besoin d'attendre explicitement dans chaque test.
 */
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
