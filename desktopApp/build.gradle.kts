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

        // Requis pour corriger WM_CLASS (voir Main.kt) : depuis Java 17 les
        // internes du toolkit X11 sont fermes par defaut.
        jvmArgs += listOf("--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED")
        nativeDistributions {
            // Un format par systeme : jpackage ne cross-compile pas, donc la CI
            // construit chacun sur son runner (Linux, Windows, macOS).
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Msi, TargetFormat.Dmg)

            // jpackage taille un runtime minimal : tout module non declare est
            // ABSENT de l app installee, meme si le JAR tourne sur un JDK complet.
            // Les six premiers viennent de `suggestRuntimeModules` ; java.sql
            // porte SQLite et jdk.httpserver le proxy de flux local.
            modules(
                "java.instrument", "java.management", "java.prefs", "java.sql",
                "jdk.httpserver", "jdk.unsupported",
                // Ces deux-la echappent a l analyse statique : ils sont charges
                // comme fournisseurs de services, jamais reference dans le code.
                // Sans jdk.crypto.ec, tout HTTPS moderne echoue a la poignee de
                // main ; sans jdk.localedata, les dates sortent en anglais.
                "jdk.crypto.ec", "jdk.localedata",
            )

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
                // Sans .ico declare, jpackage retombe sur l icone Java par
                // defaut : l app installee n avait aucun logo sur Windows.
                iconFile.set(project.file("../art/logo.ico"))
                menuGroup = "Lumen"
                perUserInstall = true
                // Identifiant fixe : les mises a jour remplacent l installation.
                upgradeUuid = "9f2c1d84-3e5b-4a77-9c31-6b5f0a2d7e14"
            }
            macOS {
                // Idem cote macOS, qui exige un .icns et rien d autre.
                iconFile.set(project.file("../art/logo.icns"))
                bundleID = "app.lumen.desktop"
            }
        }
    }
}
