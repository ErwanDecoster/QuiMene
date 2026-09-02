plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// La contrainte SwiftPM ("ressources locales à la cible", cf. Scripts/check-spec-sync.sh) ne
// s'applique pas à Gradle : un sourceSet peut pointer n'importe quel chemin relatif hors module.
// Plutôt qu'une copie committée à garder synchronisée à la main (le problème que
// check-spec-sync.sh existe pour surveiller côté Apple), cette tâche régénère la copie à chaque
// build — elle ne peut pas diverger de spec/games/ puisqu'elle n'est jamais maintenue à la main
// ni committée (docs/11-portage-android.md, étape B « Chargement des ressources »).
val specGamesDir = rootProject.layout.projectDirectory.dir("../spec/games")
val generatedResourcesDir = layout.buildDirectory.dir("generated/resources/main")

val copySpecResources =
    tasks.register<Copy>("copySpecResources") {
        from(specGamesDir)
        into(generatedResourcesDir.map { it.dir("GameDefinitions") })
        include("*.json")
    }

sourceSets {
    main {
        resources.srcDir(generatedResourcesDir)
    }
}

tasks.named("processResources") {
    dependsOn(copySpecResources)
}
