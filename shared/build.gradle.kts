import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    // AGP 9 : plugin KMP dédié, l'ancien com.android.library est incompatible
    // avec kotlin.multiplatform depuis la 9.0.
    alias(libs.plugins.android.kmp.library)
}

// Génère app/lumen/config/Secrets.kt depuis local.properties (jamais commité).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val tmdbKey: String = localProps.getProperty("tmdb.api.key", "")
val secretsDir = layout.buildDirectory.dir("generated/lumen/kotlin")
val generateSecrets by tasks.registering {
    // Copies locales : la lambda ne doit capturer AUCUNE référence au script
    // (exigence du configuration cache de Gradle 9).
    val keyLocal = tmdbKey
    val outDir = secretsDir
    inputs.property("tmdbKey", keyLocal)
    outputs.dir(outDir)
    doLast {
        val out = outDir.get().file("app/lumen/config/Secrets.kt").asFile
        out.parentFile.mkdirs()
        out.writeText(
            """
            |package app.lumen.config
            |
            |// Fichier GÉNÉRÉ depuis local.properties — ne pas éditer, ne pas commiter.
            |object Secrets {
            |    const val TMDB_API_KEY: String = "$keyLocal"
            |}
            """.trimMargin(),
        )
    }
}
tasks.configureEach {
    if (name.startsWith("compile") || name.endsWith("SourcesJar")) dependsOn(generateSecrets)
}

compose.resources {
    // Res accessible depuis :desktopApp et :androidApp (icônes de fenêtre, etc.)
    publicResClass = true
    packageOfResClass = "app.lumen.resources"
}

kotlin {
    androidLibrary {
        namespace = "app.lumen.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    jvm()

    sourceSets {
        commonMain {
            kotlin.srcDir(secretsDir)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.noarg)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.activity.compose)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.common)
            implementation(libs.ktor.client.okhttp)
        }
    }
}
