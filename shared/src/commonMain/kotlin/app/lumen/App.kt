package app.lumen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.launch
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
    val db = app.lumen.db.rememberLumenDb()
    val profileRepo = remember(db) { app.lumen.domain.ProfileRepository(db) }
    val watchRepo = remember(db) { app.lumen.domain.WatchStateRepository(db) }
    var profiles by remember { mutableStateOf(profileRepo.list()) }
    var activeProfile by remember { mutableStateOf<app.lumen.domain.LocalProfile?>(null) }
    var gateMode by remember { mutableStateOf<String?>(null) }  // null | add | manage
    var serverList by remember { mutableStateOf(store.listServers()) }
    val appScope = androidx.compose.runtime.rememberCoroutineScope()

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
      // Box racine : le bandeau de mise a jour flotte au-dessus de tout.
      Box(Modifier.fillMaxSize()) {
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
                        // Chaque bascule (création → gate → gestion → app) est ANIMÉE.
                        val homeState = when {
                            profiles.isEmpty() -> "first"
                            activeProfile == null -> gateMode ?: "gate"
                            else -> "shell"
                        }
                        AnimatedContent(
                            targetState = homeState,
                            transitionSpec = {
                                (fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 1.03f))
                                    .togetherWith(fadeOut(tween(220)))
                            },
                        ) { hs ->
                            when {
                            // Premier lancement : la création d'un profil est OBLIGATOIRE.
                            hs == "first" -> FirstProfileScreen(
                                onCreate = { name, avatar, child, maxAge, pin ->
                                    val created = profileRepo.add(name, avatar, child, maxAge, pin)
                                    profiles = profileRepo.list()
                                    activeProfile = created
                                },
                            )
                            // Puis, à CHAQUE lancement : « Qui regarde ? »,
                            // avec ajout et gestion des profils sur place.
                            hs == "add" -> FirstProfileScreen(
                                onCreate = { name, avatar, child, maxAge, pin ->
                                    profileRepo.add(name, avatar, child, maxAge, pin)
                                    profiles = profileRepo.list()
                                    gateMode = null
                                },
                            )
                            hs == "manage" -> app.lumen.ui.profiles.ProfileSettingsScreen(
                                profileRepo,
                                onBack = { gateMode = null },
                                onProfilesChanged = { profiles = profileRepo.list() },
                            )
                            hs == "gate" -> app.lumen.ui.profiles.ProfileGate(
                                profiles,
                                verifyPin = profileRepo::verifyPin,
                                onSelect = { activeProfile = it },
                                onAdd = { gateMode = "add" },
                                onManage = { gateMode = "manage" },
                            )
                            else -> Shell(
                                client, session,
                                profile = activeProfile,
                                profileRepo = profileRepo,
                                watchRepo = watchRepo,
                                onLogout = { activeProfile = null; flow.logout() },
                                onSwitchProfile = { activeProfile = null },
                                onProfilesChanged = { profiles = profileRepo.list() },
                                servers = serverList,
                                onSwitchServer = { target ->
                                    appScope.launch {
                                        if (flow.switchTo(target)) serverList = store.listServers()
                                    }
                                },
                                onAddServer = { flow.addServer() },
                                onForgetServer = { target ->
                                    store.forgetServer(target.baseUrl)
                                    serverList = store.listServers()
                                },
                            )
                            }
                        }
                    }
                }
            }
        }

        // Bandeau de mise a jour : visible partout, meme avant connexion.
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            app.lumen.ui.update.UpdateBanner(client)
        }
      }
    }
}

/** Premier lancement : création obligatoire du premier profil du foyer. */
@Composable
private fun FirstProfileScreen(
    onCreate: (name: String, avatar: String?, child: Boolean, maxAge: Int, pin: String?) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(LumenColors.Background), contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .widthIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
        ) {
            Text(
                "Crée ton profil",
                color = LumenColors.OnBackground,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Chacun a le sien : sa reprise de lecture, son avatar, et au besoin" +
                    " un code PIN ou une limite d'âge.",
                color = LumenColors.Muted,
                fontSize = 14.sp,
            )
            app.lumen.ui.profiles.ProfileEditor(
                initial = null,
                onSave = { name, avatar, child, maxAge, newPin, _ ->
                    onCreate(name, avatar, child, maxAge, newPin)
                },
                onDelete = null,
                onCancel = {},
            )
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
