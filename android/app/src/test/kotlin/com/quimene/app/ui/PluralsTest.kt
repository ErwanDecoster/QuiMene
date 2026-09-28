package com.quimene.app.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.quimene.app.R
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** Pluriels générés depuis le catalogue Apple (`Scripts/extract-android-strings.py`) : un nombre et
 * son nom s'accordent dans chaque langue, y compris dans une phrase qui en accorde plusieurs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PluralsTest {
    private fun resources(language: String): Resources {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration =
            Configuration(
                context.resources.configuration,
            ).apply { setLocale(Locale.forLanguageTag(language)) }
        return context.createConfigurationContext(configuration).resources
    }

    @Test
    fun `french singular covers zero and one`() {
        val fr = resources("fr")
        fr.getQuantityString(R.plurals.count1_points, 0, 0) shouldBe "0 point"
        fr.getQuantityString(R.plurals.count1_points, 1, 1) shouldBe "1 point"
        fr.getQuantityString(R.plurals.count1_points, 2, 2) shouldBe "2 points"
    }

    @Test
    fun `english singular is only one`() {
        val en = resources("en")
        en.getQuantityString(R.plurals.count1_points, 1, 1) shouldBe "1 point"
        en.getQuantityString(R.plurals.count1_points, 0, 0) shouldBe "0 points"
    }

    @Test
    fun `plural after another argument`() {
        resources("fr").getQuantityString(R.plurals.value1_count2_manche_s_d_affilee, 1, "Alice", 1) shouldBe
            "Alice — 1 manche d'affilée"
        resources("en").getQuantityString(R.plurals.value1_count2_manche_s_d_affilee, 3, "Alice", 3) shouldBe
            "Alice — 3 rounds in a row"
    }

    @Test
    fun `each number agrees in a composite sentence`() {
        val de = resources("de")
        de.getString(
            R.string.count1_partie_s_count2_victoire_s,
            de.getQuantityString(R.plurals.count1_partie_s_count2_victoire_s_parties, 2, 2),
            de.getQuantityString(R.plurals.count1_partie_s_count2_victoire_s_victoires, 1, 1),
        ) shouldBe "2 Spiele · 1 Sieg"
    }
}
