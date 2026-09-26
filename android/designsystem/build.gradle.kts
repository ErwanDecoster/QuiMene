// AGP 9+ intègre le support Kotlin (plus de org.jetbrains.kotlin.android séparé). Le plugin
// Compose Compiler reste nécessaire, lui, séparément.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

// com.android.library, Compose activé, zéro dépendance vers :domain — miroir de DesignSystem
// dans Package.swift (docs/11-portage-android.md, étape A).
android {
    namespace = "com.quimene.designsystem"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        // Doc 10 — zéro avertissement, comme côté Apple : un avertissement Lint fait échouer le build.
        warningsAsErrors = true
        // Vérifications « une version plus récente existe » : elles dépendent du réseau et de la
        // date, et casseraient un build reproductible dès qu'une bibliothèque sort — les montées
        // de version se font par un passage dédié.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.zxing.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
