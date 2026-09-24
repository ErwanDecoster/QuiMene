package com.quimene.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.quimene.store.QuiMeneDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * Miroir de `RoomTestBase` (`:store`), avec un exécuteur Room synchrone (`Executor { it.run() }`)
 * en plus : les DAOs Room ne dispatchent alors jamais vers un vrai thread d'arrière-plan, ce qui
 * — combiné à [MainDispatcherRule] — fait tourner à terme le travail asynchrone lancé dans le
 * `init` d'un ViewModel (`viewModelScope.launch { repository.… }`) de façon synchrone, sans
 * attente explicite nécessaire dans les tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class RoomTestBase {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    protected lateinit var db: QuiMeneDatabase

    @Before
    fun setUpDatabase() {
        val synchronousExecutor = Executor { it.run() }
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), QuiMeneDatabase::class.java)
                .setQueryExecutor(synchronousExecutor)
                .setTransactionExecutor(synchronousExecutor)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    protected fun runTest(block: suspend () -> Unit) = runBlocking { block() }
}
