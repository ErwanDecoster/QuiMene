package com.cacompte.sync

import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File

/**
 * Golden files du protocole applicatif (dossier `spec/wire`, doc 09 « Tests »), un par cas de
 * `WireMessage.Kind`. Garde-fou de portage : décode ces mêmes fixtures que
 * `WireGoldenTests.swift` et vérifie qu'on obtient une valeur équivalente, sans jamais lire le
 * code Swift pour deviner le format d'échange. Décode directement dans le type de production
 * [WireMessage] — pas de struct de fixture intermédiaire.
 */
class WireGoldenTest {
    @ParameterizedTest(name = "{0} décode et fait un aller-retour vers la même valeur")
    @MethodSource("fixtures")
    fun decodesAndRoundTrips(fixture: File) {
        val text = fixture.readText()
        val decoded = WireCodec.json.decodeFromString<WireMessage>(text)

        val reencoded = WireCodec.json.encodeToString(WireMessage.serializer(), decoded)
        val redecoded = WireCodec.json.decodeFromString<WireMessage>(reencoded)

        redecoded shouldBe decoded
    }

    @Test
    fun `the eight cases of Kind each have their fixture`() {
        val names = fixtures().map { it.nameWithoutExtension }.toSet()
        names shouldBe
            setOf(
                "hello",
                "welcome",
                "events",
                "matchChanged",
                "proposal",
                "rejection",
                "heartbeat",
                "goodbye",
            )
    }

    companion object {
        @JvmStatic
        fun fixtures(): List<File> {
            val resourceUrl =
                requireNotNull(WireGoldenTest::class.java.classLoader.getResource("WireResources")) {
                    "Ressource WireResources introuvable — copyWireResources a-t-il tourné ?"
                }
            val directory = File(resourceUrl.toURI())
            val files =
                directory.listFiles { file -> file.extension == "json" }
                    ?: error("WireResources n'est pas un dossier lisible : $directory")
            return files.sortedBy { it.name }
        }
    }
}
