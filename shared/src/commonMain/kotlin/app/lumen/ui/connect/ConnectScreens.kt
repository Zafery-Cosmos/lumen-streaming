package app.lumen.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import app.lumen.api.PublicUser
import app.lumen.resources.Res
import app.lumen.resources.logo
import org.jetbrains.compose.resources.painterResource
import app.lumen.auth.ConnectFlow
import app.lumen.auth.ConnectStep
import app.lumen.ui.theme.LumenColors
import kotlinx.coroutines.launch

/** Aiguillage du parcours de connexion — observe la machine à états et affiche l'étape. */
@Composable
fun ConnectScreen(flow: ConnectFlow) {
    val step by flow.step.collectAsState()
    Box(
        modifier = Modifier.fillMaxSize().background(LumenColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        when (val s = step) {
            is ConnectStep.Server -> ServerStep(flow, s)
            is ConnectStep.Profiles -> ProfilesStep(flow, s)
            is ConnectStep.Credentials -> CredentialsStep(flow, s)
            is ConnectStep.QuickConnect -> QuickConnectStep(flow, s)
            is ConnectStep.Done -> Unit // App() bascule sur l'accueil
        }
    }
}

@Composable
private fun ServerStep(flow: ConnectFlow, step: ConnectStep.Server) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.widthIn(max = 420.dp).padding(32.dp),
    ) {
        Wordmark()
        Text(
            "Adresse de votre serveur Jellyfin",
            color = LumenColors.Muted,
            fontSize = 14.sp,
        )
        LumenTextField(
            value = address,
            onValueChange = { address = it },
            label = "jellyfin.example.com ou 192.168.1.x",
            enabled = !step.busy,
            imeAction = ImeAction.Go,
            onSubmit = { if (address.isNotBlank()) scope.launch { flow.submitServer(address) } },
        )
        step.error?.let { ErrorText(it) }
        PrimaryButton(
            text = if (step.busy) "Recherche…" else "Continuer",
            enabled = address.isNotBlank() && !step.busy,
            onClick = { scope.launch { flow.submitServer(address) } },
        )
        if (step.busy) CircularProgressIndicator(color = LumenColors.Accent, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun ProfilesStep(flow: ConnectFlow, step: ConnectStep.Profiles) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp),
        modifier = Modifier.padding(32.dp),
    ) {
        Text(
            "Qui regarde ?",
            color = LumenColors.OnBackground,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.widthIn(max = 640.dp),
        ) {
            items(step.users, key = { it.id }) { user ->
                ProfileCard(user) { flow.pickProfile(user) }
            }
        }
        TextButton(onClick = { flow.backToServer() }) {
            Text("Changer de serveur", color = LumenColors.Muted)
        }
    }
}

@Composable
private fun ProfileCard(user: PublicUser, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp),
    ) {
        // Pastille avec l'initiale — remplacée par l'image de profil / la banque
        // d'avatars au L2 (quand Coil sera branché).
        Box(
            modifier = Modifier.size(96.dp).background(LumenColors.SurfaceHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                user.name.take(1).uppercase(),
                color = LumenColors.Accent,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(user.name, color = LumenColors.OnBackground, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CredentialsStep(flow: ConnectFlow, step: ConnectStep.Credentials) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf(step.userName ?: "") }
    var password by remember { mutableStateOf("") }
    val canSubmit = username.isNotBlank() && !step.busy

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.widthIn(max = 420.dp).padding(32.dp),
    ) {
        Text(
            step.userName ?: "Connexion",
            color = LumenColors.OnBackground,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        if (step.userName == null) {
            LumenTextField(username, { username = it }, label = "Nom d'utilisateur", enabled = !step.busy)
        }
        LumenTextField(
            value = password,
            onValueChange = { password = it },
            label = "Mot de passe",
            enabled = !step.busy,
            password = true,
            imeAction = ImeAction.Done,
            onSubmit = { if (canSubmit) scope.launch { flow.login(username, password) } },
        )
        step.error?.let { ErrorText(it) }
        PrimaryButton(
            text = if (step.busy) "Connexion…" else "Se connecter",
            enabled = canSubmit,
            onClick = { scope.launch { flow.login(username, password) } },
        )
        TextButton(onClick = { scope.launch { flow.startQuickConnect() } }) {
            Text("Utiliser Quick Connect", color = LumenColors.Accent)
        }
        TextButton(onClick = { flow.backToProfiles() }) {
            Text("Retour", color = LumenColors.Muted)
        }
    }
}

@Composable
private fun QuickConnectStep(flow: ConnectFlow, step: ConnectStep.QuickConnect) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(32.dp),
    ) {
        Text("Quick Connect", color = LumenColors.OnBackground, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Saisissez ce code dans « Paramètres → Quick Connect »\nd'un appareil déjà connecté :",
            color = LumenColors.Muted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        // Le code, bien gros — c'est fait pour être lu à travers la pièce.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            step.code.forEach { c ->
                Box(
                    modifier = Modifier.background(LumenColors.SurfaceHigh, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(c.toString(), color = LumenColors.OnBackground, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        CircularProgressIndicator(color = LumenColors.Accent, modifier = Modifier.size(26.dp))
        step.error?.let { ErrorText(it) }
        TextButton(onClick = { flow.cancelQuickConnect() }) {
            Text("Annuler", color = LumenColors.Muted)
        }
    }
}

// --- Petits composants partagés du parcours -------------------------------

@Composable
fun Wordmark() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Image(
            painter = painterResource(Res.drawable.logo),
            contentDescription = "Lumen",
            modifier = Modifier.size(110.dp),
        )
        Text(
            "LUMEN",
            color = LumenColors.OnBackground,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 8.sp,
        )
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, color = LumenColors.Accent, fontSize = 13.sp, textAlign = TextAlign.Center)
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = LumenColors.Accent,
            disabledContainerColor = LumenColors.SurfaceHigh,
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.width(280.dp).height(48.dp),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LumenTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onSubmit: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = LumenColors.Muted) },
        enabled = enabled,
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onGo = { onSubmit() },
            onDone = { onSubmit() },
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LumenColors.Accent,
            unfocusedBorderColor = LumenColors.SurfaceHigh,
            focusedTextColor = LumenColors.OnBackground,
            unfocusedTextColor = LumenColors.OnBackground,
            cursorColor = LumenColors.Accent,
        ),
        modifier = Modifier.width(320.dp),
    )
}
