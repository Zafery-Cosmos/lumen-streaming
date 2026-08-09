package app.lumen.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.api.JellyfinClient
import app.lumen.api.UserConfig
import app.lumen.auth.StoredSession
import app.lumen.domain.AppSettings
import app.lumen.ui.theme.LocalSidePadding
import app.lumen.ui.theme.LumenColors
import kotlinx.coroutines.launch

/** Sous-écran d'un groupe de paramètres — routé par sa clé. */
@Composable
fun SettingsSectionScreen(
    sectionKey: String,
    client: JellyfinClient,
    db: app.lumen.db.LumenDb,
    onLibraryChanged: () -> Unit,
    session: StoredSession,
    servers: List<StoredSession>,
    onSwitchServer: (StoredSession) -> Unit,
    onAddServer: () -> Unit,
    onForgetServer: (StoredSession) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onPlay: (app.lumen.domain.PlayRequest) -> Unit,
) {
    SectionScaffold(
        title = when (sectionKey) {
            "display" -> "Affichage"
            "home" -> "Accueil"
            "playback" -> "Lecture"
            "quality" -> "Qualité et réseau"
            "audio" -> "Audio et sous-titres"
            "addons" -> "Addons Stremio"
            "streaming" -> "Streaming et cache"
            "simkl" -> "Simkl — suivi de visionnage"
            "advanced" -> "Avancé"
            "quickconnect" -> "Connexion rapide"
            "server" -> "Service"
            else -> "Paramètres"
        },
        onBack = onBack,
    ) {
        when (sectionKey) {
            "display" -> DisplaySection()
            "home" -> HomeSection(client, session)
            "playback" -> PlaybackSection(client, session)
            "quality" -> QualitySection()
            "audio" -> AudioSection(client, session)
            "addons" -> AddonsSection(client)
            "streaming" -> StreamingSection()
            "simkl" -> SimklSection(client)
            "advanced" -> AdvancedSection(client, db, onLibraryChanged)
            "quickconnect" -> QuickConnectSection(client, session)
            "server" -> ServerSection(session, servers, onSwitchServer, onAddServer, onForgetServer, onLogout, db, onPlay, client, onLibraryChanged)
        }
    }
}

/** Charge la configuration SERVEUR de l'utilisateur et sait la réécrire. */
@Composable
private fun rememberServerConfig(
    client: JellyfinClient,
    session: StoredSession,
): Pair<UserConfig?, (UserConfig) -> Unit> {
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<UserConfig?>(null) }
    LaunchedEffect(session.userId) {
        config = runCatching { client.currentUser(session.baseUrl, session.userId).configuration }.getOrNull()
    }
    val save: (UserConfig) -> Unit = { updated ->
        config = updated
        scope.launch {
            runCatching { client.updateUserConfiguration(session.baseUrl, session.userId, updated) }
        }
    }
    return config to save
}

// --- Sections ---------------------------------------------------------------

@Composable
private fun DisplaySection() {
    SwitchRow(
        title = "Noir pur (OLED)",
        description = "Fond entièrement noir — appliqué immédiatement.",
        checked = AppSettings.oledBlack.value,
        onChecked = { AppSettings.oledBlack.set(it) },
    )
    SwitchRow(
        title = "Animations réduites",
        description = "Le hero de l'accueil ne défile plus automatiquement.",
        checked = AppSettings.reducedMotion.value,
        onChecked = { AppSettings.reducedMotion.set(it) },
    )
    NumberRow(
        title = "Intervalle du hero",
        suffix = "secondes",
        value = AppSettings.heroIntervalSec.value,
        range = 2..120,
        onValue = { AppSettings.heroIntervalSec.set(it) },
    )
    NumberRow(
        title = "Taille des pages de la médiathèque",
        suffix = "éléments",
        value = AppSettings.browsePageSize.value,
        range = 20..2000,
        onValue = { AppSettings.browsePageSize.set(it) },
    )
    SwitchRow(
        title = "Utiliser l'image de l'épisode dans « À suivre » et « Reprendre »",
        description = "Sinon, la vignette de la série est utilisée.",
        checked = AppSettings.useEpisodeImages.value,
        onChecked = { AppSettings.useEpisodeImages.set(it) },
    )
    SubHeader("Écran de veille")
    SwitchRow(
        title = "Écran de veille",
        description = "S'affiche après une période d'inactivité, n'importe où dans l'app.",
        checked = AppSettings.screensaverEnabled.value,
        onChecked = { AppSettings.screensaverEnabled.set(it) },
    )
    NumberRow(
        title = "Délai de l'écran de veille",
        suffix = "minutes d'inactivité",
        value = AppSettings.screensaverDelayMin.value,
        range = 1..120,
        onValue = { AppSettings.screensaverDelayMin.set(it) },
    )
}

private val HOME_SECTIONS = listOf(
    "resume" to "Reprendre la lecture",
    "nextup" to "À suivre",
    "recent" to "Nouveautés (médiathèque)",
    "top10" to "Top 10 cette semaine (TMDB)",
    "genres" to "Rangées par genre (TMDB)",
)

@Composable
private fun HomeSection(client: JellyfinClient, session: StoredSession) {
    val (config, saveConfig) = rememberServerConfig(client, session)

    Text(
        "Réordonne les sections avec les flèches, active ou masque chacune — appliqué en direct.",
        color = LumenColors.Muted, fontSize = 13.sp,
    )

    var order by remember {
        mutableStateOf(
            AppSettings.homeOrder.value.split(',').map { it.trim() }
                .filter { key -> HOME_SECTIONS.any { it.first == key } }
                .ifEmpty { HOME_SECTIONS.map { it.first } },
        )
    }

    fun move(key: String, delta: Int) {
        val idx = order.indexOf(key)
        val target = idx + delta
        if (idx < 0 || target !in order.indices) return
        order = order.toMutableList().apply {
            removeAt(idx)
            add(target, key)
        }
        AppSettings.homeOrder.set(order.joinToString(","))
    }

    fun prefFor(key: String) = when (key) {
        "resume" -> AppSettings.showResume
        "nextup" -> AppSettings.showNextUp
        "recent" -> AppSettings.showRecent
        "top10" -> AppSettings.showTop10
        else -> AppSettings.showGenres
    }

    order.forEachIndexed { index, key ->
        val label = HOME_SECTIONS.first { it.first == key }.second
        val pref = prefFor(key)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(LumenColors.Surface, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Column {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Monter",
                    tint = if (index > 0) LumenColors.OnBackground else LumenColors.SurfaceHigh,
                    modifier = Modifier.size(22.dp).clickable(
                        enabled = index > 0,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { move(key, -1) },
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Descendre",
                    tint = if (index < order.lastIndex) LumenColors.OnBackground else LumenColors.SurfaceHigh,
                    modifier = Modifier.size(22.dp).clickable(
                        enabled = index < order.lastIndex,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { move(key, +1) },
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                color = if (pref.value) LumenColors.OnBackground else LumenColors.Muted,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = pref.value,
                onCheckedChange = { pref.set(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = LumenColors.Accent),
            )
        }
    }

    SubHeader("Synchronisé avec le serveur")
    ServerConfigOrLoading(config) { cfg ->
        SwitchRow(
            title = "Masquer le contenu déjà vu dans « Nouveautés »",
            description = "Aussi appliqué dans le client web Jellyfin.",
            checked = AppSettings.hidePlayedInRecent.value,
            onChecked = {
                AppSettings.hidePlayedInRecent.set(it)
                saveConfig(cfg.copy(hidePlayedInLatest = it))
            },
        )
        SwitchRow(
            title = "Afficher les épisodes manquants dans les saisons",
            description = "Préférence serveur (DisplayMissingEpisodes).",
            checked = cfg.displayMissingEpisodes,
            onChecked = { saveConfig(cfg.copy(displayMissingEpisodes = it)) },
        )
        SwitchRow(
            title = "Autoriser le contenu déjà vu dans « À suivre »",
            description = "Inclut les épisodes déjà vus dans la section À suivre.",
            checked = cfg.enableRewatchingInNextUp,
            onChecked = { saveConfig(cfg.copy(enableRewatchingInNextUp = it)) },
        )
        NumberRow(
            title = "Délai d'expiration dans « À suivre »",
            suffix = "jours d'inactivité avant retrait d'une série",
            value = cfg.maxDaysForNextUp,
            range = 1..3650,
            onValue = { saveConfig(cfg.copy(maxDaysForNextUp = it)) },
        )
    }
}

@Composable
private fun PlaybackSection(client: JellyfinClient, session: StoredSession) {
    val (config, saveConfig) = rememberServerConfig(client, session)

    SwitchRow(
        title = "Reprendre automatiquement",
        description = "Sinon, la lecture repart toujours du début.",
        checked = AppSettings.resumeAlways.value,
        onChecked = { AppSettings.resumeAlways.set(it) },
    )
    NumberRow(
        title = "Durée du saut en arrière",
        suffix = "secondes",
        value = AppSettings.seekBackSec.value,
        range = 1..600,
        onValue = { AppSettings.seekBackSec.set(it) },
    )
    NumberRow(
        title = "Durée du saut en avant",
        suffix = "secondes",
        value = AppSettings.seekForwardSec.value,
        range = 1..600,
        onValue = { AppSettings.seekForwardSec.set(it) },
    )
    NumberRow(
        title = "Vitesse de lecture par défaut",
        suffix = "% (100 = normale)",
        value = AppSettings.defaultRatePct.value,
        range = 25..400,
        onValue = { AppSettings.defaultRatePct.set(it) },
    )

    ChoiceRow(
        title = "Lecteur vidéo préféré",
        options = listOf("Automatique" to "auto", "libVLC (natif)" to "vlc", "libmpv" to "mpv"),
        selected = AppSettings.playerEngine.value,
        onSelect = { AppSettings.playerEngine.set(it) },
    )
    Text(
        "libmpv sera utilisé dès qu'il est installé (mpv-libs) ; en attendant, " +
            "repli automatique sur libVLC — le moteur actif est affiché dans " +
            "« Session » des options du lecteur.",
        color = LumenColors.Muted, fontSize = 12.sp,
    )
    ChoiceRow(
        title = "Normalisation du volume",
        options = listOf("Désactivée" to "none", "Gain de piste" to "track", "Gain d'album" to "album"),
        selected = AppSettings.audioNormalization.value,
        onSelect = { AppSettings.audioNormalization.set(it) },
    )

    SubHeader("Vidéo et audio avancé")
    Text(
        "Utilisés par le profil de capacités envoyé au serveur pour négocier le" +
            " Direct Play (finalisé au lot L5).",
        color = LumenColors.Muted, fontSize = 12.sp,
    )
    SwitchRow("Préférer le conteneur fMP4 pour le HLS", null, AppSettings.preferFmp4.value) { AppSettings.preferFmp4.set(it) }
    SwitchRow("Activer le DTS (DCA)", null, AppSettings.enableDts.value) { AppSettings.enableDts.set(it) }
    SwitchRow("Activer le TrueHD", null, AppSettings.enableTrueHd.value) { AppSettings.enableTrueHd.set(it) }
    ChoiceRow(
        title = "Codec vidéo de transcodage préféré",
        options = listOf("Auto" to "auto", "H.264" to "h264", "HEVC" to "hevc", "AV1" to "av1"),
        selected = AppSettings.preferredVideoCodec.value,
        onSelect = { AppSettings.preferredVideoCodec.set(it) },
    )
    ChoiceRow(
        title = "Codec audio de transcodage préféré",
        options = listOf("Auto" to "auto", "AAC" to "aac", "AC3" to "ac3", "Opus" to "opus"),
        selected = AppSettings.preferredAudioCodec.value,
        onSelect = { AppSettings.preferredAudioCodec.set(it) },
    )

    SubHeader("Segments de média")
    Text(
        "Comportement quand le serveur signale un segment (générique, récap…).",
        color = LumenColors.Muted, fontSize = 12.sp,
    )
    val segmentOptions = listOf("Aucun" to "none", "Demander à passer" to "ask", "Passer automatiquement" to "auto")
    ChoiceRow("Générique d'intro", segmentOptions, AppSettings.segmentIntro.value) { AppSettings.segmentIntro.set(it) }
    ChoiceRow("Générique de fin", segmentOptions, AppSettings.segmentOutro.value) { AppSettings.segmentOutro.set(it) }
    ChoiceRow("Récapitulatif", segmentOptions, AppSettings.segmentRecap.value) { AppSettings.segmentRecap.set(it) }
    ChoiceRow("Prévisualisation", segmentOptions, AppSettings.segmentPreview.value) { AppSettings.segmentPreview.set(it) }
    ChoiceRow("Publicité", segmentOptions, AppSettings.segmentCommercial.value) { AppSettings.segmentCommercial.set(it) }

    SubHeader("Synchronisé avec le serveur")
    ServerConfigOrLoading(config) { cfg ->
        SwitchRow(
            title = "Lancer l'épisode suivant automatiquement",
            description = "Préférence serveur, partagée avec le client web.",
            checked = cfg.enableNextEpisodeAutoPlay,
            onChecked = {
                AppSettings.autoPlayNext.set(it)
                saveConfig(cfg.copy(enableNextEpisodeAutoPlay = it))
            },
        )
        SwitchRow(
            title = "Lire la piste audio par défaut quelle que soit la langue",
            description = null,
            checked = cfg.playDefaultAudioTrack,
            onChecked = { saveConfig(cfg.copy(playDefaultAudioTrack = it)) },
        )
        SwitchRow(
            title = "Se souvenir de la piste audio de l'élément précédent",
            description = null,
            checked = cfg.rememberAudioSelections,
            onChecked = { saveConfig(cfg.copy(rememberAudioSelections = it)) },
        )
        SwitchRow(
            title = "Se souvenir des sous-titres de l'élément précédent",
            description = null,
            checked = cfg.rememberSubtitleSelections,
            onChecked = { saveConfig(cfg.copy(rememberSubtitleSelections = it)) },
        )
    }
}

@Composable
private fun QualitySection() {
    NumberRow(
        title = "Plafond de débit",
        suffix = "Mbps (0 = automatique)",
        value = (AppSettings.defaultMaxBitrate.value / 1_000_000L).toInt(),
        range = 0..1000,
        onValue = { AppSettings.defaultMaxBitrate.set(it * 1_000_000L) },
    )
    Text(
        "Valeur libre, appliquée au lancement de chaque lecture ; modifiable en " +
            "cours de lecture dans les options du lecteur.",
        color = LumenColors.Muted, fontSize = 12.sp,
    )
    Text(
        "À savoir : ce plafond ne s'applique qu'aux titres lus depuis ton serveur " +
            "Jellyfin — lui seul peut ré-encoder à la volée. Les flux d'addons, " +
            "les torrents et les fichiers locaux sont toujours lus tels quels.",
        color = LumenColors.Muted, fontSize = 12.sp,
    )
}

@Composable
private fun AudioSection(client: JellyfinClient, session: StoredSession) {
    val (config, saveConfig) = rememberServerConfig(client, session)

    ChoiceRow(
        title = "Langue audio préférée (app)",
        options = listOf("Automatique" to "auto", "Français" to "fr", "Anglais (VO)" to "en"),
        selected = AppSettings.preferredAudioLang.value,
        onSelect = { AppSettings.preferredAudioLang.set(it) },
    )
    ChoiceRow(
        title = "Sous-titres préférés (app)",
        options = listOf("Désactivés" to "off", "Français" to "fr", "Anglais" to "en"),
        selected = AppSettings.preferredSubLang.value,
        onSelect = { AppSettings.preferredSubLang.set(it) },
    )

    SubHeader("Apparence des sous-titres")
    NumberRow(
        title = "Taille du texte",
        suffix = "% (100 = normal)",
        value = AppSettings.subtitleScalePct.value,
        range = 30..400,
        onValue = { AppSettings.subtitleScalePct.set(it) },
    )
    ChoiceRow(
        title = "Couleur du texte",
        options = listOf("Blanc" to "white", "Jaune" to "yellow", "Cyan" to "cyan", "Vert" to "green"),
        selected = AppSettings.subtitleColor.value,
        onSelect = { AppSettings.subtitleColor.set(it) },
    )
    NumberRow(
        title = "Position verticale",
        suffix = "pixels depuis le bas (0 = défaut)",
        value = AppSettings.subtitleMarginPx.value,
        range = 0..500,
        onValue = { AppSettings.subtitleMarginPx.set(it) },
    )
    Text(
        "L'apparence s'applique à la prochaine lecture (moteur libvlc).",
        color = LumenColors.Muted, fontSize = 12.sp,
    )

    SubHeader("Synchronisé avec le serveur")
    ServerConfigOrLoading(config) { cfg ->
        ChoiceRow(
            title = "Mode des sous-titres (serveur)",
            options = listOf(
                "Par défaut" to "Default",
                "Intelligent" to "Smart",
                "Uniquement forcés" to "OnlyForced",
                "Toujours" to "Always",
                "Aucun" to "None",
            ),
            selected = cfg.subtitleMode,
            onSelect = { saveConfig(cfg.copy(subtitleMode = it)) },
        )
        TextFieldRow(
            title = "Langue audio préférée (code ISO, ex. fra, eng, jpn)",
            value = cfg.audioLanguagePreference.orEmpty(),
            onValue = { saveConfig(cfg.copy(audioLanguagePreference = it.ifBlank { null })) },
        )
        TextFieldRow(
            title = "Langue de sous-titres préférée (code ISO)",
            value = cfg.subtitleLanguagePreference.orEmpty(),
            onValue = { saveConfig(cfg.copy(subtitleLanguagePreference = it.ifBlank { null })) },
        )
    }
}

@Composable
private fun AddonsSection(client: JellyfinClient) {
    val scope = rememberCoroutineScope()
    val stremio = remember { app.lumen.api.StremioClient(client.http) }
    val store = remember { app.lumen.domain.AddonStore() }
    var addons by remember { mutableStateOf(store.list()) }
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Text(
        "Colle l'URL du manifeste d'un addon Stremio (Torrentio, Frenchio…) — " +
            "les liens « stremio:// » des pages d'installation fonctionnent aussi. " +
            "Ses sources apparaîtront sur les fiches via le bouton « Sources ».",
        color = LumenColors.Muted, fontSize = 13.sp,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; error = null },
            label = { Text("URL du manifeste", color = LumenColors.Muted) },
            singleLine = true,
            colors = fieldColors(),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = {
                busy = true
                scope.launch {
                    val installed = store.install(stremio, url)
                    busy = false
                    if (installed != null) {
                        addons = store.list()
                        url = ""
                    } else {
                        error = "Manifeste invalide ou injoignable"
                    }
                }
            },
            enabled = url.isNotBlank() && !busy,
            colors = ButtonDefaults.buttonColors(containerColor = LumenColors.Accent),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(if (busy) "Vérification…" else "Installer", fontWeight = FontWeight.SemiBold)
        }
    }
    error?.let { Text(it, color = LumenColors.Accent, fontSize = 13.sp) }

    addons.forEach { addon ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(LumenColors.Surface, RoundedCornerShape(10.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(addon.name, color = LumenColors.OnBackground, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    addon.manifestUrl,
                    color = LumenColors.Muted, fontSize = 11.sp,
                    maxLines = 1,
                )
            }
            // Ouvre la page /configure de l'addon dans le navigateur.
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Configurer dans le navigateur",
                tint = LumenColors.Muted,
                modifier = Modifier.size(18.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    app.lumen.platformOpenUrl(
                        addon.manifestUrl.removeSuffix("/manifest.json") + "/configure",
                    )
                },
            )
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = addon.enabled,
                onCheckedChange = { store.toggle(addon.manifestUrl); addons = store.list() },
                colors = SwitchDefaults.colors(checkedTrackColor = LumenColors.Accent),
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Supprimer",
                tint = LumenColors.Muted,
                modifier = Modifier.size(18.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { store.remove(addon.manifestUrl); addons = store.list() },
            )
        }
    }
    if (addons.isEmpty()) {
        Text("Aucun addon installé pour l'instant.", color = LumenColors.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun StreamingSection() {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<app.lumen.player.TorrentEngineStatus?>(null) }
    var busy by remember { mutableStateOf(false) }
    var freed by remember { mutableStateOf<Long?>(null) }

    suspend fun refresh() { status = app.lumen.player.torrentEngineStatus() }
    LaunchedEffect(Unit) { refresh() }

    Text(
        "Le moteur de streaming lit les torrents des addons pendant leur " +
            "téléchargement. Ces réglages pilotent son cache disque.",
        color = LumenColors.Muted, fontSize = 13.sp,
    )

    // État du moteur, façon « URL / Statut » de Stremio.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(LumenColors.Surface, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Serveur de streaming", color = LumenColors.OnBackground, fontSize = 15.sp)
            Text(
                status?.endpoint?.ifBlank { "non démarré" } ?: "vérification…",
                color = LumenColors.Muted, fontSize = 12.sp,
            )
        }
        Text(
            when (status?.running) {
                true -> "En ligne"
                false -> "Hors ligne"
                null -> "…"
            },
            color = if (status?.running == true) Color(0xFF3ECF6B) else LumenColors.Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }

    NumberRow(
        title = "Taille du cache",
        suffix = "Gio (appliqué au prochain démarrage du moteur)",
        value = AppSettings.torrentCacheGib.value,
        range = 1..500,
        onValue = { AppSettings.torrentCacheGib.set(it) },
    )
    ChoiceRow(
        title = "Profil de torrent",
        options = listOf("Par défaut" to "default", "Mémoire vive" to "ram"),
        selected = AppSettings.torrentProfile.value,
        onSelect = { AppSettings.torrentProfile.set(it) },
    )
    Text(
        "« Mémoire vive » n'écrit rien sur le disque — idéal pour un SSD, " +
            "au prix de la RAM consommée.",
        color = LumenColors.Muted, fontSize = 12.sp,
    )
    TextFieldRow(
        title = "Dossier du cache (vide = ~/.local/share/lumen)",
        value = AppSettings.torrentCacheDir.value,
        onValue = { AppSettings.torrentCacheDir.set(it) },
    )
    TextFieldRow(
        title = "Dossier de téléchargement (vide = ~/Téléchargements)",
        value = AppSettings.downloadDir.value,
        onValue = { AppSettings.downloadDir.set(it) },
    )

    // Profil de transcodage : uniquement les périphériques réellement présents.
    val hw = remember { app.lumen.player.availableTranscodeProfiles() }
    ChoiceRow(
        title = "Profil de transcodage",
        options = listOf("Désactivé" to "none") + hw.map { it to it },
        selected = AppSettings.transcodeProfile.value,
        onSelect = { AppSettings.transcodeProfile.set(it) },
    )
    Text(
        if (hw.isEmpty()) {
            "Aucune accélération matérielle détectée sur cette machine."
        } else {
            "Décodage matériel : soulage le processeur sur les flux 4K et HEVC."
        },
        color = LumenColors.Muted, fontSize = 12.sp,
    )

    // Poids réel occupé + purge.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(LumenColors.Surface, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Cache occupé", color = LumenColors.OnBackground, fontSize = 15.sp)
            Text(
                status?.let { app.lumen.update.formatSize(it.cacheBytes) + " dans " + it.cacheDir }
                    ?: "calcul…",
                color = LumenColors.Muted, fontSize = 12.sp,
            )
            freed?.let {
                Text("${app.lumen.update.formatSize(it)} libérés", color = LumenColors.Accent, fontSize = 12.sp)
            }
        }
        Button(
            onClick = {
                busy = true
                scope.launch {
                    freed = app.lumen.player.purgeTorrentCache()
                    refresh()
                    busy = false
                }
            },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = LumenColors.Accent),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(if (busy) "Purge…" else "Vider le cache", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AdvancedSection(
    client: JellyfinClient,
    db: app.lumen.db.LumenDb,
    onLibraryChanged: () -> Unit,
) {
    val tmdb = remember { app.lumen.api.TmdbClient(client.http) }
    val hlsRepo = remember(db) { app.lumen.domain.HlsLibraryRepository(db) }
    val targetRepo = remember(db) { app.lumen.domain.UploadTargetRepository(db) }

    SubHeader("Destination d'envoi")
    UploadTargetSection(targetRepo)

    SubHeader("Dossiers HLS")
    app.lumen.ui.settings.HlsImportSection(tmdb, hlsRepo, targetRepo, onLibraryChanged)

    SubHeader("Segmentation")
    app.lumen.ui.settings.HlsConvertSection(tmdb, hlsRepo, onLibraryChanged)
}

@Composable
private fun SimklSection(client: JellyfinClient) {
    val scope = rememberCoroutineScope()
    val simkl = remember { app.lumen.api.SimklClient(client.http) }
    var pin by remember { mutableStateOf<app.lumen.api.SimklPin?>(null) }
    var waiting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val connected = AppSettings.simklToken.value.isNotBlank()

    Text(
        "Simkl garde l'historique de TOUT ce que tu regardes — y compris les " +
            "titres lus par addon, que ton serveur Jellyfin ignore complètement.",
        color = LumenColors.Muted, fontSize = 13.sp,
    )

    if (connected) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(LumenColors.Surface, RoundedCornerShape(10.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text("Compte", color = LumenColors.OnBackground, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Text(
                AppSettings.simklUser.value.ifBlank { "connecté" },
                color = LumenColors.Muted, fontSize = 13.sp,
            )
        }
        SwitchRow(
            title = "Envoyer automatiquement",
            description = "Un titre terminé à plus de 90 % part dans l'historique Simkl.",
            checked = AppSettings.simklScrobble.value,
            onChecked = { AppSettings.simklScrobble.set(it) },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                AppSettings.simklToken.set("")
                AppSettings.simklUser.set("")
            },
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = LumenColors.Accent, modifier = Modifier.size(20.dp))
            Text("Déconnecter Simkl", color = LumenColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        return
    }

    when (val p = pin) {
        null -> {
            Button(
                onClick = {
                    error = null
                    scope.launch {
                        val requested = simkl.requestPin()
                        if (requested == null) {
                            error = "Simkl injoignable — réessaie plus tard"
                            return@launch
                        }
                        pin = requested
                        waiting = true
                        // On interroge jusqu'à validation, sans bloquer l'UI.
                        val deadline = requested.expiresIn
                        var elapsed = 0
                        while (waiting && elapsed < deadline) {
                            kotlinx.coroutines.delay(requested.interval * 1000L)
                            elapsed += requested.interval
                            val token = simkl.pollPin(requested.userCode)
                            if (token != null) {
                                AppSettings.simklToken.set(token)
                                AppSettings.simklUser.set(simkl.userName(token).orEmpty())
                                waiting = false
                                pin = null
                            }
                        }
                        if (waiting) {
                            error = "Code expiré — relance la connexion"
                            waiting = false
                            pin = null
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = LumenColors.Accent),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Connecter mon compte Simkl", fontWeight = FontWeight.SemiBold)
            }
            error?.let { Text(it, color = LumenColors.Accent, fontSize = 13.sp) }
        }
        else -> {
            Text(
                "Va sur ${app.lumen.api.SimklClient.PIN_PAGE} et saisis ce code :",
                color = LumenColors.OnBackground, fontSize = 14.sp,
            )
            // Le code, en gros : il doit être lisible de loin (utile sur TV).
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                p.userCode.forEach { c ->
                    Box(
                        Modifier.background(LumenColors.SurfaceHigh, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(c.toString(), color = LumenColors.OnBackground, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(color = LumenColors.Accent, modifier = Modifier.size(18.dp))
                Text("En attente de validation…", color = LumenColors.Muted, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { app.lumen.platformOpenUrl(p.verificationUrl.ifBlank { app.lumen.api.SimklClient.PIN_PAGE }) },
                    colors = ButtonDefaults.buttonColors(containerColor = LumenColors.SurfaceHigh),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = LumenColors.OnBackground, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ouvrir la page", color = LumenColors.OnBackground)
                }
                Text(
                    "Annuler",
                    color = LumenColors.Muted,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { waiting = false; pin = null }.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickConnectSection(client: JellyfinClient, session: StoredSession) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Boolean?>(null) }

    Text(
        "Saisis le code affiché par un autre appareil pour l'autoriser sur ton compte.",
        color = LumenColors.Muted, fontSize = 13.sp,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = code,
            onValueChange = { v -> if (v.length <= 6 && v.all { it.isDigit() }) { code = v; result = null } },
            label = { Text("Code à 6 chiffres", color = LumenColors.Muted) },
            singleLine = true,
            colors = fieldColors(),
            modifier = Modifier.width(220.dp),
        )
        Button(
            onClick = { scope.launch { result = client.quickConnectAuthorize(session.baseUrl, code) } },
            enabled = code.length == 6,
            colors = ButtonDefaults.buttonColors(containerColor = LumenColors.Accent),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("Autoriser", fontWeight = FontWeight.SemiBold)
        }
    }
    result?.let {
        Text(
            if (it) "Appareil autorisé — il est en train de se connecter." else "Code refusé par le serveur.",
            color = if (it) LumenColors.Muted else LumenColors.Accent,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ServerSection(
    current: StoredSession,
    servers: List<StoredSession>,
    onSwitchServer: (StoredSession) -> Unit,
    onAddServer: () -> Unit,
    onForgetServer: (StoredSession) -> Unit,
    onLogout: () -> Unit,
    db: app.lumen.db.LumenDb,
    onPlay: (app.lumen.domain.PlayRequest) -> Unit,
    client: JellyfinClient,
    onLibraryChanged: () -> Unit,
) {
    Text(
        "Bascule d'un serveur à l'autre sans te reconnecter — la session de " +
            "chaque serveur est mémorisée.",
        color = LumenColors.Muted, fontSize = 13.sp,
    )
    servers.forEach { server ->
        val isCurrent = server.baseUrl == current.baseUrl
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
                .background(
                    if (isCurrent) LumenColors.SurfaceHigh else LumenColors.Surface,
                    RoundedCornerShape(10.dp),
                )
                .clickable(
                    enabled = !isCurrent,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onSwitchServer(server) }
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    server.serverName.ifEmpty { server.baseUrl },
                    color = LumenColors.OnBackground,
                    fontSize = 15.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                )
                Text(
                    "${server.baseUrl} · ${server.userName}",
                    color = LumenColors.Muted,
                    fontSize = 12.sp,
                )
            }
            if (isCurrent) {
                Text("Actuel", color = LumenColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Oublier ce serveur",
                    tint = LumenColors.Muted,
                    modifier = Modifier.size(18.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onForgetServer(server) },
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
            onClick = onAddServer,
        ).padding(vertical = 6.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = LumenColors.Accent, modifier = Modifier.size(20.dp))
        Text("Ajouter un serveur", color = LumenColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }

    val storageRepo = remember(db) { app.lumen.domain.StorageSourceRepository(db) }
    val webdavRepo = remember(db) { app.lumen.domain.WebDavSourceRepository(db) }
    val ftpRepo = remember(db) { app.lumen.domain.FtpSourceRepository(db) }
    val bucketRepo = remember(db) { app.lumen.domain.BucketLibraryRepository(db) }
    val tmdb = remember { app.lumen.api.TmdbClient(client.http) }

    SubHeader("Stockage perso — S3, R2, B2")
    StorageSourcesSection(storageRepo, bucketRepo, tmdb, onPlay, onLibraryChanged)

    SubHeader("WebDAV")
    WebDavSourcesSection(webdavRepo, onPlay)

    SubHeader("FTP")
    FtpSourcesSection(ftpRepo, onPlay)

    Spacer(Modifier.size(10.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onLogout,
        ),
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = LumenColors.Accent, modifier = Modifier.size(20.dp))
        Text("Se déconnecter du serveur actuel", color = LumenColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// --- Composants communs ------------------------------------------------------

@Composable
private fun ServerConfigOrLoading(config: UserConfig?, content: @Composable (UserConfig) -> Unit) {
    if (config == null) {
        CircularProgressIndicator(color = LumenColors.Accent, modifier = Modifier.size(22.dp))
    } else {
        content(config)
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(
        text,
        color = LumenColors.OnBackground,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun SectionScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        // Contenu CENTRÉ dans la page, pas collé à gauche.
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().background(LumenColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(start = LocalSidePadding.current, end = LocalSidePadding.current, top = 96.dp, bottom = 48.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = LumenColors.OnBackground,
                    modifier = Modifier.size(24.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack,
                    ),
                )
                Text(title, color = LumenColors.OnBackground, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            content()
            SaveSettingsRow()
        }
    }
}

/**
 * Bouton « Sauvegarder » — les réglages sont écrits dès qu'on les touche,
 * mais certains (cache, profil de torrent, transcodage) ne prennent effet
 * qu'au redémarrage du moteur de streaming. Ce bouton le fait, et confirme.
 */
@Composable
private fun SaveSettingsRow() {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(top = 10.dp),
    ) {
        Button(
            onClick = {
                saving = true
                saved = false
                scope.launch {
                    // Relance le moteur pour que cache et profils s'appliquent.
                    runCatching { app.lumen.player.restartTorrentEngine() }
                    saving = false
                    saved = true
                }
            },
            enabled = !saving,
            colors = ButtonDefaults.buttonColors(containerColor = LumenColors.Accent),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(if (saving) "Application…" else "Sauvegarder les paramètres", fontWeight = FontWeight.SemiBold)
        }
        if (saved) {
            Text("Paramètres appliqués", color = LumenColors.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SwitchRow(title: String, description: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(LumenColors.Surface, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = LumenColors.OnBackground, fontSize = 15.sp)
            description?.let { Text(it, color = LumenColors.Muted, fontSize = 12.sp) }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedTrackColor = LumenColors.Accent),
        )
    }
}

/** Champ NUMÉRIQUE LIBRE — la personnalisation n'est pas bridée à des presets. */
@Composable
private fun NumberRow(title: String, suffix: String, value: Int, range: IntRange, onValue: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(LumenColors.Surface, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = LumenColors.OnBackground, fontSize = 15.sp)
            Text(suffix, color = LumenColors.Muted, fontSize = 12.sp)
        }
        BasicTextField(
            value = text,
            onValueChange = { v ->
                if (v.length <= 6 && v.all { it.isDigit() }) {
                    text = v
                    v.toIntOrNull()?.let { n -> if (n in range) onValue(n) }
                }
            },
            singleLine = true,
            textStyle = TextStyle(
                color = LumenColors.OnBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            ),
            cursorBrush = SolidColor(LumenColors.Accent),
            modifier = Modifier.width(84.dp)
                .background(LumenColors.SurfaceHigh, RoundedCornerShape(8.dp))
                .padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun TextFieldRow(title: String, value: String, onValue: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().background(LumenColors.Surface, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(title, color = LumenColors.OnBackground, fontSize = 14.sp)
        BasicTextField(
            value = text,
            onValueChange = { text = it; onValue(it) },
            singleLine = true,
            textStyle = TextStyle(color = LumenColors.OnBackground, fontSize = 14.sp),
            cursorBrush = SolidColor(LumenColors.Accent),
            modifier = Modifier.width(220.dp)
                .background(LumenColors.SurfaceHigh, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().background(LumenColors.Surface, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(title, color = LumenColors.OnBackground, fontSize = 15.sp)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (label, value) ->
                val isSel = value == selected
                Text(
                    label,
                    color = if (isSel) Color.Black else LumenColors.OnBackground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(
                            if (isSel) LumenColors.OnBackground else LumenColors.SurfaceHigh,
                            RoundedCornerShape(16.dp),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(value) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LumenColors.Accent,
    unfocusedBorderColor = LumenColors.SurfaceHigh,
    focusedTextColor = LumenColors.OnBackground,
    unfocusedTextColor = LumenColors.OnBackground,
    cursorColor = LumenColors.Accent,
)
