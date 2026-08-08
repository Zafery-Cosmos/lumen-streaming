import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvm()
    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.desktop.currentOs)
            implementation(compose.components.resources)
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.lumen.desktop.MainKt"
        nativeDistributions {
            // Un format par systeme : jpackage ne cross-compile pas, donc la CI
            // construit chacun sur son runner (Linux, Windows, macOS).
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "lumen"
            packageVersion = "1.0.0"
            description = "Lumen Streaming — client Jellyfin natif"
            vendor = "Zafery-Cosmos"

            linux {
                iconFile.set(project.file("../art/logo-master.png"))
                menuGroup = "AudioVideo"
                debMaintainer = "odilon.hugonnot@gmail.com"
            }
            windows {
                menuGroup = "Lumen"
                perUserInstall = true
                // Identifiant fixe : les mises a jour remplacent l installation.
                upgradeUuid = "9f2c1d84-3e5b-4a77-9c31-6b5f0a2d7e14"
            }
            macOS {
                bundleID = "app.lumen.desktop"
            }
        }
    }
}
