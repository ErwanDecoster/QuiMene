plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Kotlin/JVM pur, comme Sync ne dépend côté Swift que de Domain + supabase-swift — le moteur
// HTTP de Ktor (ktor-client-okhttp) est un artefact JVM ordinaire, pas Android-spécifique, même
// contrainte des deux côtés (docs/11-portage-android.md, étape A).
kotlin {
    jvmToolchain(17)
}

// La contrainte SwiftPM ne s'applique pas à Gradle (voir :catalog) — événements de session
// scellés par le code Swift (spec/session/*.json, doc 16) copiés à chaque build : Android doit les
// relire à l'identique.
val specSessionDir = rootProject.layout.projectDirectory.dir("../spec/session")
val generatedTestResourcesDir = layout.buildDirectory.dir("generated/resources/test")

val copySessionResources =
    tasks.register<Copy>("copySessionResources") {
        from(specSessionDir)
        into(generatedTestResourcesDir.map { it.dir("SessionResources") })
        include("*.json")
    }

sourceSets {
    test {
        resources.srcDir(generatedTestResourcesDir)
    }
}

tasks.named("processTestResources") {
    dependsOn(copySessionResources)
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.realtime.kt)
    implementation(libs.supabase.postgrest.kt)
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}
