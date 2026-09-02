plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Kotlin/JVM pur, comme Sync ne dépend côté Swift que de Domain + supabase-swift — le moteur
// HTTP de Ktor (ktor-client-okhttp) est un artefact JVM ordinaire, pas Android-spécifique, même
// contrainte des deux côtés (docs/11-portage-android.md, étape A). Vide à cette étape :
// le client supabase-kt (canal/presence/broadcast), WireMessage, SessionCrypto sont l'étape F,
// hors périmètre de cette session.
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":domain"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotest.assertions.core)
}

tasks.test {
    useJUnitPlatform()
}
