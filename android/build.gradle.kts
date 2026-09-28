plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
}

// ktlint est le seul outil de style pour tous les modules — appliqué une fois ici plutôt que
// répété dans chaque build.gradle.kts (rôle équivalent à .swift-format/Scripts/lint.sh côté
// Apple, un seul réglage pour tout le monorepo Android).
subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

    // Doc 10 — zéro avertissement, comme côté Apple (SWIFT_TREAT_WARNINGS_AS_ERRORS) : un
    // avertissement du compilateur Kotlin fait échouer le build de n'importe quel module.
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
        compilerOptions.allWarningsAsErrors.set(true)
    }
}
