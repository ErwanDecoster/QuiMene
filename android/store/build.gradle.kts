// AGP 9+ intègre le support Kotlin (plus de org.jetbrains.kotlin.android séparé).
plugins {
    alias(libs.plugins.android.library)
}

// com.android.library (pas kotlin("jvm")) : Room a besoin d'un Context Android, contrairement à
// :domain/:catalog/:sync qui restent Kotlin/JVM pur. Vide à cette étape — l'implémentation Room
// (entités, DAO, repositories) est l'étape D (docs/11-portage-android.md), hors périmètre de
// cette session.
android {
    namespace = "com.cacompte.store"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":domain"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotest.assertions.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
