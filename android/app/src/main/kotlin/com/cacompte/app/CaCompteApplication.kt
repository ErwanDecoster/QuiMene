package com.cacompte.app

import android.app.Application
import com.cacompte.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class CaCompteApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob())

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, applicationScope)
    }

    override fun onTerminate() {
        applicationScope.cancel()
        super.onTerminate()
    }
}
