package app.lumen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.JellyfinClient
import app.lumen.auth.ConnectFlow
import app.lumen.auth.ConnectStep
import app.lumen.auth.ServerResolver
import app.lumen.auth.SessionStore
import app.lumen.auth.StoredSession
import app.lumen.ui.connect.ConnectScreen
import app.lumen.ui.connect.Wordmark
import app.lumen.ui.theme.LumenColors
import app.lumen.ui.theme.LumenTheme

/**
 * Racine de l'UI partagée. L1 : parcours de connexion complet
 * (serveur → profils → identifiants / Quick Connect → session persistée),
 * puis un accueil provisoire qui prouve que la session est réelle.
 */
@Composable
fun App() {
    // Composition racine des services — remplacée par Koin quand l'app grossira (L2).
    val store = remember { SessionStore() }
    val client = remember { JellyfinClient(deviceId = store.deviceId, deviceName = platformDeviceName()) }
    val flow = remember { ConnectFlow(client, ServerResolver(client), store) }

    var restoring by remember { mutableStateOf(true) }

    // Reconnexion silencieuse au lancement (plan §2) — l'UI d'attente reste sobre.
    LaunchedEffect(Unit) {
        flow.tryRestore()
        restoring = false
    }

    LumenTheme {
        val step by flow.step.collectAsState()
        when {
            restoring -> SplashScreen()
            step is ConnectStep.Done -> HomePlaceholder(
                session = (step as ConnectStep.Done).session,
                onLogout = { flow.logout() },
            )
            else -> ConnectScreen(flow)
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

/** Accueil provisoire — remplacé par le vrai accueil Netflix-like au L3. */
@Composable
private fun HomePlaceholder(session: StoredSession, onLogout: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(LumenColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Wordmark()
            Text(
                "Bonjour ${session.userName}",
                color = LumenColors.OnBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Connecté à ${session.serverName.ifEmpty { session.baseUrl }}",
                color = LumenColors.Muted,
                fontSize = 14.sp,
            )
            TextButton(onClick = onLogout) {
                Text("Se déconnecter", color = LumenColors.Accent)
            }
        }
    }
}
