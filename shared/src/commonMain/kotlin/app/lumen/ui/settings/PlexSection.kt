package app.lumen.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.PlexClient
import app.lumen.api.PlexResource
import app.lumen.api.PlexSection as PlexLibrarySection
import app.lumen.domain.PlexLibraryRepository
import app.lumen.domain.PlexSource
import app.lumen.domain.PlexSourceRepository
import app.lumen.i18n.T
import app.lumen.ui.theme.LumenColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Connexion à un compte Plex.
 *
 * Aucune clé d'API à réclamer, aucun mot de passe à taper ici : Lumen ouvre un
 * code à quatre caractères, l'utilisateur le valide sur plex.tv/link, et Plex
 * délivre un jeton lié à SON compte. C'est le mécanisme prévu pour les
 * appareils, et il évite que l'app manipule le mot de passe.
 */
@Composable
fun PlexSectionUi(
    sourceRepo: PlexSourceRepository,
    libraryRepo: PlexLibraryRepository,
    client: PlexClient,
    onLibraryChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf(sourceRepo.list()) }
    var wizard by remember { mutableStateOf(false) }
    var indexing by remember { mutableStateOf<String?>(null) }
    var indexResult by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }

    Text(T["plex.intro"], color = LumenColors.Muted, fontSize = 13.sp)

    sources.forEach { s ->
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(LumenColors.Surface, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.serverName, color = LumenColors.OnBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        s.baseUrl + " · " + if (s.sections.isEmpty()) {
                            T["plex.toutesBibliotheques"]
                        } else {
                            T.format("plex.nBibliotheques", s.sections.size)
                        },
                        color = LumenColors.Muted, fontSize = 11.sp,
                    )
                }
                if (indexing == s.id) {
                    CircularProgressIndicator(color = LumenColors.Accent, modifier = Modifier.size(16.dp))
                } else {
                    Text(
                        T["plex.indexer"],
                        color = LumenColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            indexing = s.id
                            indexResult = null
                            scope.launch {
                                val r = app.lumen.domain.PlexIndexer.index(s, client, libraryRepo)
                                indexResult = r.fold(
                                    onSuccess = { Triple(s.id, T.format("plex.titresIndexes", it), false) },
                                    onFailure = { Triple(s.id, T.format("plex.echec", it.message ?: ""), true) },
                                )
                                indexing = null
                                if (r.isSuccess) onLibraryChanged()
                            }
                        },
                    )
                }
                Spacer(Modifier.width(14.dp))
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = T["plex.retirer"],
                    tint = LumenColors.Muted,
                    modifier = Modifier.size(18.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        sourceRepo.remove(s.id)
                        sources = sourceRepo.list()
                        onLibraryChanged()
                    },
                )
            }
            indexResult?.takeIf { it.first == s.id }?.let { (_, message, failed) ->
                Text(
                    message,
                    color = if (failed) LumenColors.Accent else LumenColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { wizard = true }.padding(vertical = 6.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = LumenColors.Accent, modifier = Modifier.size(20.dp))
        Text(T["plex.connecter"], color = LumenColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }

    if (wizard) {
        PlexWizard(
            client = client,
            onDismiss = { wizard = false },
            onDone = { source ->
                sourceRepo.add(source)
                sources = sourceRepo.list()
                wizard = false
            },
        )
    }
}

/** Code → serveur → bibliothèques, en trois temps. */
@Composable
private fun PlexWizard(
    client: PlexClient,
    onDismiss: () -> Unit,
    onDone: (PlexSource) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(1) }
    var code by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var servers by remember { mutableStateOf<List<PlexResource>>(emptyList()) }
    var chosen by remember { mutableStateOf<PlexResource?>(null) }
    var baseUrl by remember { mutableStateOf("") }
    var libraries by remember { mutableStateOf<List<PlexLibrarySection>>(emptyList()) }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }

    // Étape 1 : on ouvre un code, puis on interroge Plex jusqu'à validation.
    // Toutes les 2 s : assez réactif pour l'utilisateur, assez espacé pour ne
    // pas marteler le service pendant les minutes où il cherche son téléphone.
    LaunchedEffect(Unit) {
        runCatching {
            val pin = client.createPin()
            code = pin.code
            repeat(150) {
                delay(2_000)
                val polled = client.pollPin(pin.id)
                val t = polled.authToken
                if (!t.isNullOrBlank()) {
                    token = t
                    busy = true
                    servers = client.servers(t)
                    busy = false
                    step = 2
                    return@runCatching
                }
            }
            error = T["plex.codeExpire"]
        }.onFailure { error = it.message ?: T["plex.echecConnexion"] }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LumenColors.Surface,
        title = {
            Text(
                when (step) {
                    1 -> T["plex.connexion"]
                    2 -> T["plex.choisirServeur"]
                    else -> T["plex.choisirBibliotheques"]
                },
                color = LumenColors.OnBackground,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(it, color = LumenColors.Accent, fontSize = 12.sp) }
                when (step) {
                    1 -> {
                        Text(T["plex.instructions"], color = LumenColors.Muted, fontSize = 13.sp)
                        if (code.isBlank()) {
                            CircularProgressIndicator(color = LumenColors.Accent, modifier = Modifier.size(20.dp))
                        } else {
                            Text(
                                code,
                                color = LumenColors.Accent,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                PlexClient.LINK_PAGE,
                                color = LumenColors.OnBackground,
                                fontSize = 14.sp,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { app.lumen.platformOpenUrl(PlexClient.LINK_PAGE) },
                            )
                            Text(T["plex.attente"], color = LumenColors.Muted, fontSize = 12.sp)
                        }
                    }
                    2 -> {
                        if (busy) CircularProgressIndicator(color = LumenColors.Accent, modifier = Modifier.size(20.dp))
                        if (servers.isEmpty() && !busy) {
                            Text(T["plex.aucunServeur"], color = LumenColors.Muted, fontSize = 13.sp)
                        }
                        servers.forEach { s ->
                            val uri = client.bestConnection(s)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .background(
                                        if (chosen?.clientIdentifier == s.clientIdentifier) {
                                            LumenColors.SurfaceHigh
                                        } else LumenColors.Surface,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable(
                                        enabled = uri != null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { chosen = s; baseUrl = uri.orEmpty() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, color = LumenColors.OnBackground, fontSize = 14.sp)
                                    Text(
                                        uri ?: T["plex.aucuneAdresse"],
                                        color = LumenColors.Muted, fontSize = 11.sp, maxLines = 1,
                                    )
                                }
                                if (s.owned) {
                                    Text(T["plex.proprietaire"], color = Color(0xFF3ECF6B), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    else -> {
                        Text(T["plex.bibliothequesIntro"], color = LumenColors.Muted, fontSize = 12.sp)
                        if (busy) CircularProgressIndicator(color = LumenColors.Accent, modifier = Modifier.size(20.dp))
                        libraries.forEach { lib ->
                            val on = lib.key in picked
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    picked = if (on) picked - lib.key else picked + lib.key
                                }.padding(vertical = 4.dp),
                            ) {
                                Icon(
                                    if (on) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    tint = if (on) LumenColors.Accent else LumenColors.Muted,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text("${lib.title} (${lib.type})", color = LumenColors.OnBackground, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val label = if (step == 3) T["plex.enregistrer"] else T["plex.suivant"]
            val enabled = when (step) {
                1 -> false
                2 -> chosen != null && baseUrl.isNotBlank()
                else -> true
            }
            Text(
                label,
                color = if (enabled) LumenColors.Accent else LumenColors.Muted,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (step == 2) {
                        step = 3
                        busy = true
                        scope.launch {
                            runCatching { client.sections(baseUrl, token) }
                                .onSuccess { libraries = it.filter { s -> s.type == "movie" } }
                                .onFailure { error = it.message }
                            busy = false
                        }
                    } else {
                        onDone(
                            PlexSource(
                                id = "",
                                label = chosen?.name.orEmpty(),
                                token = token,
                                serverName = chosen?.name.orEmpty(),
                                baseUrl = baseUrl,
                                sections = picked.toList(),
                            ),
                        )
                    }
                }.padding(8.dp),
            )
        },
        dismissButton = {
            Text(
                T["plex.annuler"], color = LumenColors.Muted,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() }.padding(8.dp),
            )
        },
    )
}
