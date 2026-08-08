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
            targetFormats(TargetFormat.Rpm, TargetFormat.AppImage, TargetFormat.Msi)
            packageName = "lumen"
            packageVersion = "0.1.0"
        }
    }
}
