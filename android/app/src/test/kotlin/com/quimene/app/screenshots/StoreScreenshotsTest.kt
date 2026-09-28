package com.quimene.app.screenshots

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.quimene.app.R
import com.quimene.app.di.AppContainer
import com.quimene.app.di.LocalAppContainer
import com.quimene.app.navigation.QuiMeneApp
import com.quimene.app.navigation.RootDestination
import com.quimene.designsystem.theme.QuiMeneTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/**
 * Doc 10 « Captures des stores » — miroir de `StoreScreenshotTests.swift` : mêmes données de démo
 * (`spec/screenshots/demo-data.json`), mêmes écrans, mêmes noms de fichiers. Ne tourne que si
 * `Scripts/store-screenshots.sh` passe `quimene.screenshots.locales` (voir `app/build.gradle.kts`).
 *
 * Rendu par Robolectric, sans émulateur : SDK 30 pour garder la palette de la marque (les couleurs
 * dynamiques n'existent qu'à partir de l'API 31, elles suivraient ici un fond d'écran arbitraire),
 * et une application nue plutôt que `QuiMeneApplication`, dont le passage au premier plan
 * synchroniserait avec le serveur.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreScreenshotsTest(
    private val locale: String,
    private val device: Device,
) {
    /** Proportions d'appareils réels, dans la limite de la Play Console (le plus grand côté ≤ 2 × le
     * plus petit) : un téléphone récent est plus allongé que 1:2, les tablettes sont en 10:16. Les
     * slides des fiches (`store/slides`) les recadrent ensuite en 9:16. */
    enum class Device(
        val folder: String,
        val qualifiers: String,
    ) {
        Phone("phone", "w400dp-h800dp-port-notnight-xxhdpi"), // 1200 × 2400
        Tablet7("tablet-7", "w600dp-h960dp-port-notnight-xhdpi"), // 1200 × 1920
        Tablet10("tablet-10", "w800dp-h1280dp-port-notnight-xhdpi"), // 1600 × 2560
    }

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val scope = CoroutineScope(SupervisorJob())

    @After
    fun cancelScope() {
        scope.cancel()
    }

    @Test
    fun capture() {
        assumeTrue("Réservé à Scripts/store-screenshots.sh.", locale.isNotEmpty())
        val tag = Locale.forLanguageTag(locale)
        RuntimeEnvironment.setQualifiers("${tag.language}-r${tag.country}-${device.qualifiers}")
        // `LocalizedText` (catalogue des jeux) suit la langue par défaut de la JVM.
        Locale.setDefault(tag)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context, scope, syncsSharedProfiles = false)
        runBlocking { DemoData.seed(File(System.getProperty(DEMO_DATA_PROPERTY)!!), container) }

        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                QuiMeneTheme(darkTheme = false) {
                    CompositionLocalProvider(LocalAppContainer provides container) { QuiMeneApp() }
                }
            }
        }

        composeRule.waitForText("Emma")
        snapshot("06-joueurs")

        openTab(RootDestination.Games, context)
        composeRule.waitForTag("resume-match")
        snapshot("03-jeux")

        composeRule.onAllNodes(hasTestTag("resume-match")).onFirst().performClick()
        // Le badge « Moi » arrive par une seconde lecture, après le tableau des scores.
        composeRule.waitForText(context.getString(R.string.moi))
        snapshot("01-partie")
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        openTab(RootDestination.History, context)
        composeRule.waitForTag("history-match")
        snapshot("05-historique")

        composeRule.onAllNodes(hasTestTag("history-match")).onFirst().performClick()
        snapshot("02-resultats")
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        openTab(RootDestination.Profile, context)
        snapshot("04-profil")

        scenario.close()
    }

    private fun openTab(
        tab: RootDestination,
        context: Context,
    ) {
        composeRule.onAllNodes(hasText(context.getString(tab.labelRes))).onFirst().performClick()
    }

    /** Les écrans lisent Room hors du fil principal : attendre le contenu, pas seulement l'inactivité
     * de Compose. */
    private fun ComposeTestRule.waitForTag(tag: String) {
        waitUntil(TIMEOUT_MS) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeTestRule.waitForText(text: String) {
        waitUntil(TIMEOUT_MS) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun snapshot(name: String) {
        // Un écran qui charge (résultats, profil) affiche un indicateur indéterminé jusqu'à la fin
        // de ses lectures Room ; la pause couvre ceux qui n'en affichent pas.
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule
                .onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
                .fetchSemanticsNodes()
                .isEmpty()
        }
        Thread.sleep(SETTLE_MS)
        composeRule.waitForIdle()
        val bitmap =
            composeRule
                .onAllNodes(isRoot())
                .onFirst()
                .captureToImage()
                .asAndroidBitmap()
        // PNG 24 bits : la Play Console refuse un canal alpha.
        bitmap.setHasAlpha(false)
        val file = File(System.getProperty(OUTPUT_PROPERTY)!!, "$locale/${device.folder}/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        private const val LOCALES_PROPERTY = "quimene.screenshots.locales"
        private const val OUTPUT_PROPERTY = "quimene.screenshots.output"
        private const val DEMO_DATA_PROPERTY = "quimene.screenshots.demoData"
        private const val TIMEOUT_MS = 10_000L
        private const val SETTLE_MS = 500L

        /** Sans langue demandée, un seul cas vide, sauté par `assumeTrue` : un lanceur paramétré
         * sans aucun paramètre ferait échouer `test`. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1}")
        fun parameters(): List<Array<Any>> {
            val locales =
                System
                    .getProperty(LOCALES_PROPERTY)
                    .orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            if (locales.isEmpty()) return listOf(arrayOf("", Device.Phone))
            return locales.flatMap { locale -> Device.entries.map { arrayOf(locale, it) } }
        }
    }
}
