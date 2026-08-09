import java.util.Properties

plugins {
    // AGP 9 : le support Kotlin est intégré, on n'applique plus kotlin-android.
    // Le code composable vit dans :shared (androidMain) — ce module reste du Kotlin pur.
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
}

/**
 * Contournement d'un trou d'intégration entre AGP 9 et Compose Multiplatform.
 *
 * :shared cible Android via le nouveau plugin `com.android.kotlin.multiplatform.library`
 * (DSL `androidLibrary {}`). Compose Resources y prépare bien les images
 * (`:shared:prepareComposeResourcesTaskForAndroidMain`) mais la tâche censée les
 * copier dans les assets Android — `copyAndroidMainComposeResourcesToAndroidAssets` —
 * a sa propriété `outputDirectory` jamais configurée : elle échoue si on la lance,
 * et personne ne la lance jamais. Résultat gardé du logo/des avatars : l'APK ne
 * contient AUCUNE ressource composeResources, et l'app plante au premier
 * `painterResource(Res.drawable.…)` — dont le tout premier, sur l'écran de
 * démarrage. Confirmé en inspectant l'APK (0 entrée composeResources) et en
 * lançant la tâche à la main (`Value not set … outputDirectory`).
 *
 * On refait donc cette copie nous-mêmes, au chemin que le lecteur runtime
 * attend réellement : `assets/composeResources/<packageOfResClass>/…`.
 */
val composeAssetsDir = layout.buildDirectory.dir("generated/lumenComposeAssets")
val copyComposeResourcesToAssets by tasks.registering(Copy::class) {
    // Le dossier lu est produit par les tâches « …ForCommonMain » : sans ces
    // dépendances explicites, Gradle 9 refuse la tâche (dépendance implicite).
    dependsOn(
        ":shared:prepareComposeResourcesTaskForAndroidMain",
        ":shared:prepareComposeResourcesTaskForCommonMain",
        ":shared:copyNonXmlValueResourcesForCommonMain",
    )
    from(
        project(":shared").layout.buildDirectory.dir(
            "generated/compose/resourceGenerator/preparedResources/commonMain/composeResources",
        ),
    )
    into(composeAssetsDir.map { it.dir("composeResources/app.lumen.resources") })
}
// `assets.srcDir` ne suffit pas à ORDONNER l'exécution : sans ce rattachement
// explicite, mergeAssets peut s'exécuter avant que la copie n'ait eu lieu.
//
// ATTENTION au motif : la tâche s'appelle « mergeDebugAssets » /
// « mergeReleaseAssets ». Un filtre sur « MergeAssets » ne correspond à RIEN,
// la copie n'était donc jamais déclenchée et l'APK partait sans ses images —
// l'app plantait au lancement sur MissingResourceException(logo.png).
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(copyComposeResourcesToAssets)
}

// Clé de signature de Lumen, déclarée dans local.properties (jamais commitée) :
//   lumen.keystore=/chemin/vers/lumen.keystore
// Android REFUSE de mettre à jour une app signée par une autre clé. Sans ce
// verrouillage, un build fait sur une autre machine produit un APK que les
// appareils existants ne peuvent pas installer — il faudrait désinstaller, donc
// tout perdre. On échoue bruyamment plutôt que de produire un APK inutilisable.
val lumenKeystorePath: String? = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("lumen.keystore")

android {
    namespace = "app.lumen.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    signingConfigs {
        create("lumen") {
            val ks = lumenKeystorePath?.let { file(it) }
            if (ks != null && ks.exists()) {
                storeFile = ks
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    defaultConfig {
        applicationId = "app.lumen"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 11
        versionName = "1.8.0"
    }

    buildTypes {
        val lumenSigning = if (lumenKeystorePath != null && file(lumenKeystorePath).exists()) {
            signingConfigs.getByName("lumen")
        } else {
            null
        }
        debug {
            lumenSigning?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            lumenSigning?.let { signingConfig = it }
        }
    }

}

// L'ancienne API `sourceSets { assets.srcDir(...) }` est acceptée par le DSL
// mais mergeDebugAssets l'ignore silencieusement sous AGP 9 : verifié en
// inspectant intermediates/assets/debug/mergeDebugAssets, qui ne contenait
// QUE l'asset d'OkHttp. La Variant API est le seul chemin qui fonctionne
// réellement pour cette version d'AGP.
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory(composeAssetsDir.get().asFile.absolutePath)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
}
