// AGP 9+ intègre le support Kotlin (plus de org.jetbrains.kotlin.android séparé).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// com.android.library (pas kotlin("jvm")) : Room a besoin d'un Context Android, contrairement à
// :domain/:catalog/:sync qui restent Kotlin/JVM pur.
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(17)
}

// Room versionne son schéma en JSON à chaque changement (room.schemaLocation) — nécessaire pour
// tester les migrations plus tard (étape D, doc 03 « Migrations »), même avec un plan encore
// vide à la v1.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // `api`, pas `implementation` : l'API publique de :store expose Room directement
    // (`CaCompteDatabase` hérite de `RoomDatabase`, les DAOs sont un type Room) — :app en a
    // besoin sur son propre classpath de compilation pour construire/utiliser la base.
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    // Room a besoin du runner JUnit4 de Robolectric — ce module teste donc en JUnit4, pas
    // JUnit5 comme :domain/:catalog (convention standard des tests Android/Room, pas une
    // incohérence : @RunWith(RobolectricTestRunner) n'existe pas côté JUnit Platform).
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(kotlin("test-junit"))
}
