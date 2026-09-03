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

// La contrainte SwiftPM ne s'applique pas à Gradle (voir :catalog) — golden files du protocole
// applicatif (spec/wire/*.json, doc 09) copiés à chaque build plutôt que dupliqués à la main.
val specWireDir = rootProject.layout.projectDirectory.dir("../spec/wire")
val generatedTestResourcesDir = layout.buildDirectory.dir("generated/resources/test")

val copyWireResources =
    tasks.register<Copy>("copyWireResources") {
        from(specWireDir)
        into(generatedTestResourcesDir.map { it.dir("WireResources") })
        include("*.json")
    }

sourceSets {
    test {
        resources.srcDir(generatedTestResourcesDir)
    }
}

tasks.named("processTestResources") {
    dependsOn(copyWireResources)
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}
