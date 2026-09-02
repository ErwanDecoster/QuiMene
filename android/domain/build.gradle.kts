plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Kotlin/JVM pur : aucune dépendance au SDK Android sur le classpath, donc aucun moyen d'y
// importer android.* même par erreur — la contrainte "Domain ne connaît aucune implémentation
// concrète de GameRules" (docs/04-moteur-de-regles.md) devient vérifiable par construction du
// module, pas seulement par convention. Miroir de la cible Domain de Package.swift.
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
