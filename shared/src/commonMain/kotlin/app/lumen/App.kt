package app.lumen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.lumen.api.JellyfinClient
import app.lumen.auth.ConnectFlow
import app.lumen.auth.ConnectStep
import app.lumen.auth.ServerResolver
import app.lumen.auth.SessionStore
import app.lumen.ui.connect.ConnectScreen
import app.lumen.ui.connect.Wordmark
import app.lumen.ui.shell.Shell
import app.lumen.ui.theme.LumenColors
import app.lumen.ui.theme.LumenTheme
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade

/** Les trois grands états de l'app — la transition entre eux est toujours animée. */
private enum class RootState { Splash, Connect, Home }

@Composable
fun App() {
    // Composition racine des services — remplacée par Koin quand l'app grossira.
    val store = remember { SessionStore() }
    val client = remember { JellyfinClient(deviceId = store.deviceId, deviceName = platformDeviceName()) }
    val flow = remember { ConnectFlow(client, ServerResolver(client), store) }
    val profileStore = remember { app.lumen.domain.ProfileStore() }
    var profiles by remember { mutableStateOf(profileStore.list()) }
    var activeProfile by remember { mutableStateOf<app.lumen.domain.LocalProfile?>(null) }

    // Coil : toutes les AsyncImage passent par Ktor, avec crossfade systématique.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .crossfade(220)
            .build()
    }

    var restoring by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        flow.tryRestore()  // reconnexion silencieuse (plan §2)
        restoring = false
    }

    LumenTheme {
        val step by flow.step.collectAsState()
        val root = when {
            restoring -> RootState.Splash
            step is ConnectStep.Done -> RootState.Home
            else -> RootState.Connect
        }

        // Transition racine : fondu + léger zoom — jamais de bascule sèche.
        AnimatedContent(
            targetState = root,
            transitionSpec = {
                (fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 1.04f))
                    .togetherWith(fadeOut(tween(250)))
            },
        ) { state ->
            when (state) {
                RootState.Splash -> SplashScreen()
                RootState.Connect -> ConnectScreen(flow)
                RootState.Home -> {
                    val session = (flow.step.value as? ConnectStep.Done)?.session
                    if (session != null) {
                        if (profiles.isNotEmpty() && activeProfile == null) {
                            // « Qui regarde ? » — seulement si des profils existent.
                            app.lumen.ui.profiles.ProfileGate(profiles) { activeProfile = it }
                        } else {
                            Shell(
                                client, session,
                                profile = activeProfile,
                                profileStore = profileStore,
                                onLogout = { activeProfile = null; flow.logout() },
                                onSwitchProfile = { activeProfile = null },
                                onProfilesChanged = { profiles = profileStore.list() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(LumenColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Wordmark()
    }
}
