// AGP 9+ intègre le support Kotlin (plus de org.jetbrains.kotlin.android séparé — incompatible
// avec la nouvelle DSL AGP). Le plugin Compose Compiler reste nécessaire, lui, séparément.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.cacompte.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cacompte.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(17)
    // Scaffold/TopAppBar/DropdownMenu (Material 3) restent marqués expérimentaux dans cette
    // version de la bibliothèque bien qu'ils soient l'API recommandée — pratique standard des
    // projets Compose Material 3, pas un contournement ponctuel d'un avertissement.
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":catalog"))
    implementation(project(":store"))
    implementation(project(":sync"))
    implementation(project(":designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.code.scanner)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // JUnit4 + Robolectric — pas JUnit5 (voir :store) : les ViewModels de cette étape touchent
    // Android (Context via AppContainer/Application) et Robolectric exige son propre runner.
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}
