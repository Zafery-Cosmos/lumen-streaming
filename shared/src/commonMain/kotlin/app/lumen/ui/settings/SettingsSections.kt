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
import app.lumen.i18n.T
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
            "display" -> T["settingsSections.affichageEtAccueil"]
            "playback" -> T["settingsSections.lectureQualiteEtAudio"]
            "streaming" -> T["settingsSections.streamingEtCache"]
            "simkl" -> T["settingsSections.simklSuiviDeVisionnage"]
            "advanced" -> T["settingsSections.avance"]
            "quickconnect" -> T["settingsSections.connexionRapide"]
            "server" -> "Service"
            else -> T["settingsSections.parametres"]
        },
        onBack = onBack,
    ) {
        when (sectionKey) {
            "display" -> DisplayHomeSection(client, session)
            "playback" -> PlaybackQualityAudioSection(client, session)
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

/** Affichage + Accueil réunis : deux réglages du même ordre (apparence de l'app). */
@Composable
private fun DisplayHomeSection(client: JellyfinClient, session: StoredSession) {
    DisplaySection()
    SubHeader("Accueil")
    HomeSection(client, session)
}

@Composable
private fun DisplaySection() {
    ChoiceRow(
        title = app.lumen.i18n.T["lang.title"],
        options = listOf(app.lumen.i18n.T["lang.auto"] to "auto") +
            app.lumen.i18n.Lang.entries.map { it.label to it.code },
        selected = AppSettings.language.value,
        onSelect = { AppSettings.language.set(it) },
    )
    Text(app.lumen.i18n.T["lang.note"], color = LumenColors.Muted, fontSize = 12.sp)

    SwitchRow(
        title = T["settingsSections.noirPurOled"],
        description = T["settingsSections.fondEntierementNoirAppliqueImmediatement"],
        checked = AppSettings.oledBlack.value,
        onChecked = { AppSettings.oledBlack.set(it) },
    )
    SwitchRow(
        title = T["settingsSections.animationsReduites"],
        description = T["settingsSections.leHeroDeLAccueilNe"],
        checked = AppSettings.reducedMotion.value,
        onChecked = { AppSettings.reducedMotion.set(it) },
    )
    NumberRow(
        title = T["settingsSections.intervalleDuHero"],
        suffix = "secondes",
        value = AppSettings.heroIntervalSec.value,
        range = 2..120,
        onValue = { AppSettings.heroIntervalSec.set(it) },
    )
    NumberRow(
        title = T["settingsSections.tailleDesPagesDeLaMediatheque"],
        suffix = T["settingsSections.elements"],
        value = AppSettings.browsePageSize.value,
        range = 20..2000,
        onValue = { AppSettings.browsePageSize.set(it) },
    )
    SwitchRow(
        title = T["settingsSections.utiliserLImageDeLEpisode"],
        description = T["settingsSections.sinonLaVignetteDeLaSerie"],
        checked = AppSettings.useEpisodeImages.value,
        onChecked = { AppSettings.useEpisodeImages.set(it) },
    )
    SubHeader(T["settingsSections.ecranDeVeille"])
    SwitchRow(
        title = T["settingsSections.ecranDeVeille"],
        description = T["settingsSections.sAfficheApresUnePeriodeD"],
        checked = AppSettings.screensaverEnabled.value,
        onChecked = { AppSettings.screensaverEnabled.set(it) },
    )
    NumberRow(
        title = T["settingsSections.delaiDeLEcranDeVeille"],
        suffix = T["settingsSections.minutesDInactivite"],
        value = AppSettings.screensaverDelayMin.value,
        range = 1..120,
        onValue = { AppSettings.screensaverDelayMin.set(it) },
    )
}

private val HOME_SECTIONS = listOf(
    "resume" to T["settingsSections.reprendreLaLecture"],
    "nextup" to T["settingsSections.aSuivre"],
    "recent" to T["settingsSections.nouveautesMediatheque"],
    "top10" to T["settingsSections.top10CetteSemaineTmdb"],
    "genres" to T["settingsSections.rangeesParGenreTmdb"],
)

@Composable
private fun HomeSection(client: JellyfinClient, session: StoredSession) {
    val (config, saveConfig) = rememberServerConfig(client, session)

    Text(
        T["settingsSections.reordonneLesSectionsAvecLesFleches"],
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

    SubHeader(T["settingsSections.synchroniseAvecLeServeur"])
    ServerConfigOrLoading(config) { cfg ->
        SwitchRow(
            title = T["settingsSections.masquerLeContenuDejaVuDans"],
            description = T["settingsSections.aussiAppliqueDansLeClientWeb"],
            checked = AppSettings.hidePlayedInRecent.value,
            onChecked = {
                AppSettings.hidePlayedInRecent.set(it)
                saveConfig(cfg.copy(hidePlayedInLatest = it))
            },
        )
        SwitchRow(
            title = T["settingsSections.afficherLesEpisodesManquantsDansLes"],
            description = T["settingsSections.preferenceServeurDisplaymissingepisodes"],
            checked = cfg.displayMissingEpisodes,
            onChecked = { saveConfig(cfg.copy(displayMissingEpisodes = it)) },
        )
        SwitchRow(
            title = T["settingsSections.autoriserLeContenuDejaVuDans"],
            description = T["settingsSections.inclutLesEpisodesDejaVusDans"],
            checked = cfg.enableRewatchingInNextUp,
            onChecked = { saveConfig(cfg.copy(enableRewatchingInNextUp = it)) },
        )
        NumberRow(
            title = T["settingsSections.delaiDExpirationDansASuivre"],
            suffix = T["settingsSections.joursDInactiviteAvantRetraitD"],
            value = cfg.maxDaysForNextUp,
            range = 1..3650,
            onValue = { saveConfig(cfg.copy(maxDaysForNextUp = it)) },
        )
    }
}

/** Lecture + Qualité/réseau + Audio/sous-titres réunis : tout ce qui régit LA lecture. */
@Composable
private fun PlaybackQualityAudioSection(client: JellyfinClient, session: StoredSession) {
    PlaybackSection(client, session)
    SubHeader(T["settingsSections.qualiteEtReseau"])
    QualitySection()
    SubHeader(T["settingsSections.audioEtSousTitres"])
    AudioSection(client, session)
}

@Composable
private fun PlaybackSection(client: JellyfinClient, session: StoredSession) {
    val (config, saveConfig) = rememberServerConfig(client, session)

    SwitchRow(
        title = T["settingsSections.reprendreAutomatiquement"],
        description = T["settingsSections.sinonLaLectureRepartToujoursDu"],
        checked = AppSettings.resumeAlways.value,
        onChecked = { AppSettings.resumeAlways.set(it) },
    )
    NumberRow(
        title = T["settingsSections.dureeDuSautEnArriere"],
        suffix = "secondes",
        value = AppSettings.seekBackSec.value,
        range = 1..600,
        onValue = { AppSettings.seekBackSec.set(it) },
    )
    NumberRow(
        title = T["settingsSections.dureeDuSautEnAvant"],
        suffix = "secondes",
        value = AppSettings.seekForwardSec.value,
        range = 1..600,
        onValue = { AppSettings.seekForwardSec.set(it) },
    )
    NumberRow(
        title = T["settingsSections.vitesseDeLectureParDefaut"],
        suffix = T["settingsSections.100Normale"],
        value = AppSettings.defaultRatePct.value,
        range = 25..400,
        onValue = { AppSettings.defaultRatePct.set(it) },
    )

    ChoiceRow(
        title = T["settingsSections.lecteurVideoPrefere"],
        options = listOf("Automatique" to "auto", T["settingsSections.libvlcNatif"] to "vlc", "libmpv" to "mpv"),
        selected = AppSettings.playerEngine.value,
        onSelect = { AppSettings.playerEngine.set(it) },
    )
    Text(
        T["settingsSections.libmpvSeraUtiliseDesQuIl"],
        color = LumenColors.Muted, fontSize = 12.sp,
    )
    ChoiceRow(
        title = T["settingsSections.normalisationDuVolume"],
        options = listOf(T["settingsSections.desactivee"] to "none", T["settingsSections.gainDePiste"] to "track", T["settingsSections.gainDAlbum"] to "album"),
        selected = AppSettings.audioNormalization.value,
        onSelect = { AppSettings.audioNormalization.set(it) },
    )

    SubHeader(T["settingsSections.videoEtAudioAvance"])
    Text(
        T["settingsSections.utilisesParLeProfilDeCapacites"],
        color = LumenColors.Muted, fontSize = 12.sp,
    )
    SwitchRow(T["settingsSections.prefererLeConteneurFmp4PourLe"], null, AppSettings.preferFmp4.value) { AppSettings.preferFmp4.set(it) }
    SwitchRow(T["settingsSections.activerLeDtsDca"], null, AppSettings.enableDts.value) { AppSettings.enableDts.set(it) }
    SwitchRow(T["settingsSections.activerLeTruehd"], null, AppSettings.enableTrueHd.value) { AppSettings.enableTrueHd.set(it) }
    ChoiceRow(
        title = T["settingsSections.codecVideoDeTranscodagePrefere"],
        options = listOf("Auto" to "auto", "H.264" to "h264", "HEVC" to "hevc", "AV1" to "av1"),
        selected = AppSettings.preferredVideoCodec.value,
        onSelect = { AppSettings.preferredVideoCodec.set(it) },
    )
    ChoiceRow(
        title = T["settingsSections.codecAudioDeTranscodagePrefere"],
        options = listOf("Auto" to "auto", "AAC" to "aac", "AC3" to "ac3", "Opus" to "opus"),
        selected = AppSettings.preferredAudioCodec.value,
        onSelect = { AppSettings.preferredAudioCodec.set(it) },
    )

    SubHeader(T["settingsSections.segmentsDeMedia"])
    Text(
        T["settingsSections.comportementQuandLeServeurSignaleUn"],
        color = LumenColors.Muted, fontSize = 12.sp,
    )
    val segmentOptions = listOf("Aucun" to "none", T["settingsSections.demanderAPasser"] to "ask", T["settingsSections.passerAutomatiquement"] to "auto")
    ChoiceRow(T["settingsSections.generiqueDIntro"], segmentOptions, AppSettings.segmentIntro.value) { AppSettings.segmentIntro.set(it) }
    ChoiceRow(T["settingsSections.generiqueDeFin"], segmentOptions, AppSettings.segmentOutro.value) { AppSettings.segmentOutro.set(it) }
    ChoiceRow(T["settingsSections.recapitulatif"], segmentOptions, AppSettings.segmentRecap.value) { AppSettings.segmentRecap.set(it) }
    ChoiceRow(T["settingsSections.previsualisation"], segmentOptions, AppSettings.segmentPreview.value) { AppSettings.segmentPreview.set(it) }
    ChoiceRow(T["settingsSections.publicite"], segmentOptions, AppSettings.segmentCommercial.value) { AppSettings.segmentCommercial.set(it) }

    SubHeader(T["settingsSections.synchroniseAvecLeServeur"])
    ServerConfigOrLoading(config) { cfg ->
        SwitchRow(
            title = T["settingsSections.lancerLEpisodeSuivantAutomatiquement"],
            description = T["settingsSections.preferenceServeurPartageeAvecLeClient"],
            checked = cfg.enableNextEpisodeAutoPlay,
            onChecked = {
                AppSettings.autoPlayNext.set(it)
                saveConfig(cfg.copy(enableNextEpisodeAutoPlay = it))
            },
        )
        SwitchRow(
            title = T["settingsSections.lireLaPisteAudioParDefaut"],
            description = null,
            checked = cfg.playDefaultAudioTrack,
            onChecked = { saveConfig(cfg.copy(playDefaultAudioTrack = it)) },
        )
        SwitchRow(
            title = T["settingsSections.seSouvenirDeLaPisteAudio"],
            description = null,
            checked = cfg.rememberAudioSelections,
            onChecked = { saveConfig(cfg.copy(rememberAudioSelections = it)) },
        )
        SwitchRow(
            title = T["settingsSections.seSouvenirDesSousTitresDe"],
            description = null,
            checked = cfg.rememberSubtitleSelections,
            onChecked = { saveConfig(cfg.copy(rememberSubtitleSelections = it)) },
        )
    }
}

/**
 * Résolution approximative pour un débit donné — mêmes paliers que le
 * sélecteur rapide du lecteur, pour qu'un même débit veuille dire la même
 * chose aux deux endroits.
 */
private fun approxResolutionForBitrate(mbps: Int): String = when {
    mbps <= 0 -> T["settingsSections.automatiqueAucunPlafond"]
    mbps >= 16 -> T["settingsSections.2160p4kEtPlus"]
    mbps >= 8 -> "1080p"
    mbps >= 4 -> "720p"
    mbps >= 2 -> "480p"
    else -> T["settingsSections.360pOuMoins"]
}

@Composable
private fun QualitySection() {
    val mbps = (AppSettings.defaultMaxBitrate.value / 1_000_000L).toInt()
    NumberRow(
        title = T["settingsSections.plafondDeDebit"],
        suffix = T["settingsSections.mbps0Automatique"],
        value = mbps,
        range = 0..1000,
        onValue = { AppSettings.defaultMaxBitrate.set(it * 1_000_000L) },
    )
    Text(
        // Un débit seul ne dit rien à qui ne le connaît pas par cœur — les
        // mêmes paliers que le sélecteur rapide du lecteur (Direct Play).
        "≈ ${approxResolutionForBitrate(mbps)}",
        color = LumenColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
    )
    Text(
        T["settingsSections.valeurLibreAppliqueeAuLancementDe"],
        color = LumenColors.Muted, fontSize = 12.sp,
    )
    Text(
        T["settingsSections.aSavoirCePlafondNeS"],
        color = LumenColors.Muted, fontSize = 12.sp,
    )
}

@Composable
private fun AudioSection(client: JellyfinClient, session: StoredSession) {
    val (config, saveConfig) = rememberServerConfig(client, session)

    ChoiceRow(
        title = T["settingsSections.langueAudioPrefereeApp"],
        options = listOf("Automatique" to "auto", T["settingsSections.francais"] to "fr", T["settingsSections.anglaisVo"] to "en"),
        selected = AppSettings.preferredAudioLang.value,
        onSelect = { AppSettings.preferredAudioLang.set(it) },
    )
    ChoiceRow(
        title = T["settingsSections.sousTitresPreferesApp"],
        options = listOf(T["settingsSections.desactives"] to "off", T["settingsSections.francais"] to "fr", "Anglais" to "en"),
        selected = AppSettings.preferredSubLang.value,
        onSelect = { AppSettings.preferredSubLang.set(it) },
    )

    SubHeader(T["settingsSections.apparenceDesSousTitres"])
    NumberRow(
        title = T["settingsSections.tailleDuTexte"],
        suffix = T["settingsSections.100Normal"],
        value = AppSettings.subtitleScalePct.value,
        range = 30..400,
        onValue = { AppSettings.subtitleScalePct.set(it) },
    )
    ChoiceRow(
        title = T["settingsSections.couleurDuTexte"],
        options = listOf("Blanc" to "white", "Jaune" to "yellow", "Cyan" to "cyan", "Vert" to "green"),
        selected = AppSettings.subtitleColor.value,
        onSelect = { AppSettings.subtitleColor.set(it) },
    )
    NumberRow(
        title = T["settingsSections.positionVerticale"],
        suffix = T["settingsSections.pixelsDepuisLeBas0Defaut"],
        value = AppSettings.subtitleMarginPx.value,
        range = 0..500,
        onValue = { AppSettings.subtitleMarginPx.set(it) },
    )
    Text(
        T["settingsSections.lApparenceSAppliqueALa"],
        color = LumenColors.Muted, fontSize = 12.sp,
    )

    SubHeader(T["settingsSections.synchroniseAvecLeServeur"])
    ServerConfigOrLoading(config) { cfg ->
        ChoiceRow(
            title = T["settingsSections.modeDesSousTitresServeur"],
            options = listOf(
                T["settingsSections.parDefaut"] to "Default",
                "Intelligent" to "Smart",
                T["settingsSections.uniquementForces"] to "OnlyForced",
                "Toujours" to "Always",
                "Aucun" to "None",
            ),
            selected = cfg.subtitleMode,
            onSelect = { saveConfig(cfg.copy(subtitleMode = it)) },
        )
        TextFieldRow(
            title = T["settingsSections.langueAudioPrefereeCodeIsoEx"],
            value = cfg.audioLanguagePreference.orEmpty(),
            onValue = { saveConfig(cfg.copy(audioLanguagePreference = it.ifBlank { null })) },
        )
        TextFieldRow(
            title = T["settingsSections.langueDeSousTitresPrefereeCode"],
            value = cfg.subtitleLanguagePreference.orEmpty(),
            onValue = { saveConfig(cfg.copy(subtitleLanguagePreference = it.ifBlank { null })) },
        )
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
        T["settingsSections.leMoteurDeStreamingLitLes"],
        color = LumenColors.Muted, fontSize = 13.sp,
    )

    // État du moteur, façon « URL / Statut » de Stremio.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(LumenColors.Surface, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(T["settingsSections.serveurDeStreaming"], color = LumenColors.OnBackground, fontSize = 15.sp)
            Text(
                status?.endpoint?.ifBlank { T["settingsSections.nonDemarre"] } ?: T["settingsSections.verification"],
                color = LumenColors.Muted, fontSize = 12.sp,
            )
        }
        Text(
            when (status?.running) {
                true -> T["settingsSections.enLigne"]
                false -> T["settingsSections.horsLigne"]
                null -> "…"
            },
            color = if (status?.running == true) Color(0xFF3ECF6B) else LumenColors.Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }

    var serverUrl by remember { mutableStateOf(AppSettings.torrentServerUrl.value) }
    TextFieldRow(
        title = T["settingsSections.adresseDuServeurDeStreamingVide"],
        value = serverUrl,
        onValue = { serverUrl = it },
    )
    Text(
        T["settingsSections.pourUtiliserUnTorrserverQuiTourne"],
        color = LumenColors.Muted, fontSize = 12.sp,
    )
    Text(
        T["settingsSections.appliquerEtReconnecter"],
        color = LumenColors.Accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
            AppSettings.torrentServerUrl.set(serverUrl.trim())
            busy = true
            scope.launch {
                app.lumen.player.restartTorrentEngine()
                refresh()
                busy = false
            }
        }.padding(vertical = 4.dp),
    )

    NumberRow(
        title = T["settingsSections.tailleDuCache"],
        suffix = T["settingsSections.gioAppliqueAuProchainDemarrageDu"],
        value = AppSettings.torrentCacheGib.value,
        range = 1..500,
        onValue = { AppSettings.torrentCacheGib.set(it) },
    )
    ChoiceRow(
        title = T["settingsSections.profilDeTorrent"],
        options = listOf(T["settingsSections.parDefaut"] to "default", T["settingsSections.memoireVive"] to "ram"),
        selected = AppSettings.torrentProfile.value,
        onSelect = { AppSettings.torrentProfile.set(it) },
    )
    Text(
        T["settingsSections.memoireViveNEcritRienSur"],
        color = LumenColors.Muted, fontSize = 12.sp,
    )
    TextFieldRow(
        title = T["settingsSections.dossierDuCacheVideLocalShare"],
        value = AppSettings.torrentCacheDir.value,
        onValue = { AppSettings.torrentCacheDir.set(it) },
    )
    TextFieldRow(
        title = T["settingsSections.dossierDeTelechargementVideTelechargements"],
        value = AppSettings.downloadDir.value,
        onValue = { AppSettings.downloadDir.set(it) },
    )

    // Profil de transcodage : uniquement les périphériques réellement présents.
    val hw = remember { app.lumen.player.availableTranscodeProfiles() }
    ChoiceRow(
        title = T["settingsSections.profilDeTranscodage"],
        options = listOf(T["settingsSections.desactive"] to "none") + hw.map { it to it },
        selected = AppSettings.transcodeProfile.value,
        onSelect = { AppSettings.transcodeProfile.set(it) },
    )
    Text(
        if (hw.isEmpty()) {
            T["settingsSections.aucuneAccelerationMaterielleDetecteeSurCette"]
        } else {
            T["settingsSections.decodageMaterielSoulageLeProcesseurSur"]
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
            Text(T["settingsSections.cacheOccupe"], color = LumenColors.OnBackground, fontSize = 15.sp)
            Text(
                status?.let { app.lumen.update.formatSize(it.cacheBytes) + " dans " + it.cacheDir }
                    ?: "calcul…",
                color = LumenColors.Muted, fontSize = 12.sp,
            )
            freed?.let {
                Text(T.format("settingsSections.liberes", app.lumen.update.formatSize(it)), color = LumenColors.Accent, fontSize = 12.sp)
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
            Text(if (busy) "Purge…" else T["settingsSections.viderLeCache"], fontWeight = FontWeight.SemiBold)
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

    SubHeader(T["settingsSections.destinationDEnvoi"])
    UploadTargetSection(targetRepo)

    SubHeader(T["settingsSections.dossiersHls"])
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
        T["settingsSections.simklGardeLHistoriqueDeTout"],
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
                AppSettings.simklUser.value.ifBlank { T["settingsSections.connecte"] },
                color = LumenColors.Muted, fontSize = 13.sp,
            )
        }
        SwitchRow(
            title = T["settingsSections.envoyerAutomatiquement"],
            description = T["settingsSections.unTitreTermineAPlusDe"],
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
            Text(T["settingsSections.deconnecterSimkl"], color = LumenColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
                            error = T["settingsSections.simklInjoignableReessaiePlusTard"]
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
                            error = T["settingsSections.codeExpireRelanceLaConnexion"]
                            waiting = false
                            pin = null
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = LumenColors.Accent),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(T["settingsSections.connecterMonCompteSimkl"], fontWeight = FontWeight.SemiBold)
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
                Text(T["settingsSections.enAttenteDeValidation"], color = LumenColors.Muted, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { app.lumen.platformOpenUrl(p.verificationUrl.ifBlank { app.lumen.api.SimklClient.PIN_PAGE }) },
                    colors = ButtonDefaults.buttonColors(containerColor = LumenColors.SurfaceHigh),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = LumenColors.OnBackground, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(T["settingsSections.ouvrirLaPage"], color = LumenColors.OnBackground)
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
        T["settingsSections.saisisLeCodeAfficheParUn"],
        color = LumenColors.Muted, fontSize = 13.sp,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = code,
            onValueChange = { v -> if (v.length <= 6 && v.all { it.isDigit() }) { code = v; result = null } },
            label = { Text(T["settingsSections.codeA6Chiffres"], color = LumenColors.Muted) },
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
            if (it) T["settingsSections.appareilAutoriseIlEstEnTrain"] else T["settingsSections.codeRefuseParLeServeur"],
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
        T["settingsSections.basculeDUnServeurAL"],
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
                    contentDescription = T["settingsSections.oublierCeServeur"],
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
        Text(T["settingsSections.ajouterUnServeur"], color = LumenColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }

    val storageRepo = remember(db) { app.lumen.domain.StorageSourceRepository(db) }
    val webdavRepo = remember(db) { app.lumen.domain.WebDavSourceRepository(db) }
    val ftpRepo = remember(db) { app.lumen.domain.FtpSourceRepository(db) }
    val bucketRepo = remember(db) { app.lumen.domain.BucketLibraryRepository(db) }
    val tmdb = remember { app.lumen.api.TmdbClient(client.http) }

    SubHeader(T["settingsSections.stockagePersoS3R2B2"])
    StorageSourcesSection(storageRepo, bucketRepo, tmdb, onPlay, onLibraryChanged)

    SubHeader("WebDAV")
    WebDavSourcesSection(webdavRepo, onPlay)

    SubHeader("FTP")
    FtpSourcesSection(ftpRepo, onPlay)

    val plexSourceRepo = remember(db) { app.lumen.domain.PlexSourceRepository(db) }
    val plexLibraryRepo = remember(db) { app.lumen.domain.PlexLibraryRepository(db) }
    // L'identifiant client est propre à cette installation et STABLE : Plex
    // s'en sert pour reconnaître l'appareil dans les autorisations du compte.
    val plexClient = remember(client) {
        app.lumen.api.PlexClient(client.http, clientId = app.lumen.domain.plexClientId())
    }
    SubHeader("Plex")
    PlexSectionUi(plexSourceRepo, plexLibraryRepo, plexClient, onLibraryChanged)

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
        Text(T["settingsSections.seDeconnecterDuServeurActuel"], color = LumenColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
            Text(if (saving) "Application…" else T["settingsSections.sauvegarderLesParametres"], fontWeight = FontWeight.SemiBold)
        }
        if (saved) {
            Text(T["settingsSections.parametresAppliques"], color = LumenColors.Muted, fontSize = 13.sp)
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
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LumenColors.Accent,
    unfocusedBorderColor = LumenColors.SurfaceHigh,
    focusedTextColor = LumenColors.OnBackground,
    unfocusedTextColor = LumenColors.OnBackground,
    cursorColor = LumenColors.Accent,
)
