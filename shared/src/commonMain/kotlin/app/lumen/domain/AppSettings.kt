package app.lumen.domain

import androidx.compose.runtime.mutableStateOf
import com.russhwolf.settings.Settings

/**
 * Réglages de l'app (plan §6) : persistés ET observables — chaque valeur est un
 * état Compose, donc tout changement se répercute immédiatement dans l'UI.
 * Aucun réglage décoratif : tout ce qui est ici est branché quelque part.
 */
object AppSettings {
    private val store = Settings()

    class BoolPref internal constructor(private val key: String, default: Boolean) {
        private val state = mutableStateOf(store.getBoolean(key, default))
        val value: Boolean get() = state.value
        fun set(v: Boolean) {
            state.value = v
            store.putBoolean(key, v)
        }
    }

    class IntPref internal constructor(private val key: String, default: Int) {
        private val state = mutableStateOf(store.getInt(key, default))
        val value: Int get() = state.value
        fun set(v: Int) {
            state.value = v
            store.putInt(key, v)
        }
    }

    class LongPref internal constructor(private val key: String, default: Long) {
        private val state = mutableStateOf(store.getLong(key, default))
        val value: Long get() = state.value
        fun set(v: Long) {
            state.value = v
            store.putLong(key, v)
        }
    }

    class StringPref internal constructor(private val key: String, default: String) {
        private val state = mutableStateOf(store.getString(key, default))
        val value: String get() = state.value
        fun set(v: String) {
            state.value = v
            store.putString(key, v)
        }
    }

    // --- Affichage ---------------------------------------------------------
    /** Noir pur OLED au lieu du noir bleuté — appliqué par LumenTheme. */
    val oledBlack = BoolPref("display.oled", false)
    /** Animations réduites : le hero ne défile plus tout seul. */
    val reducedMotion = BoolPref("display.reducedmotion", false)
    /** Nombre d'éléments chargés dans les grilles Films/Séries. */
    val browsePageSize = IntPref("display.pagesize", 200)

    // --- Accueil : ordre ET visibilité des rangées -------------------------
    /** Ordre des sections, séparées par des virgules — réordonnable dans l'UI. */
    val homeOrder = StringPref("home.order", "resume,nextup,recent,top10,genres")
    val showResume = BoolPref("home.resume", true)
    val showNextUp = BoolPref("home.nextup", true)
    val showRecent = BoolPref("home.recent", true)
    val showTop10 = BoolPref("home.top10", true)
    val showGenres = BoolPref("home.genres", true)

    // --- Lecture -----------------------------------------------------------
    /** true → reprendre là où on s'est arrêté ; false → toujours du début. */
    val resumeAlways = BoolPref("play.resume", true)
    /** Sauts avant et arrière SÉPARÉS (comme Jellyfin), en secondes. */
    val seekForwardSec = IntPref("play.seekfwd", 30)
    val seekBackSec = IntPref("play.seekback", 10)
    /** Vitesse de lecture par défaut, en pourcent (100 = normale). */
    val defaultRatePct = IntPref("play.rate", 100)
    /** Lecture auto de l'épisode suivant (branché au L12 — enchaînement). */
    val autoPlayNext = BoolPref("play.autonext", true)

    // --- Sous-titres -------------------------------------------------------
    /** Échelle du texte des sous-titres, en pourcent (desktop/libvlc). */
    val subtitleScalePct = IntPref("subs.scale", 100)
    /** Couleur du texte : white | yellow | cyan | green. */
    val subtitleColor = StringPref("subs.color", "white")
    /** Position verticale : marge depuis le bas, en pixels. */
    val subtitleMarginPx = IntPref("subs.margin", 0)

    // --- Affichage (suite) -------------------------------------------------
    /** Intervalle de défilement du hero, en secondes. */
    val heroIntervalSec = IntPref("display.herointerval", 8)

    // --- Accueil (suite) ---------------------------------------------------
    /** Miroir local de HidePlayedInLatest (aussi synchronisé côté serveur). */
    val hidePlayedInRecent = BoolPref("home.hideplayed", false)

    // --- Lecteur & audio avancé --------------------------------------------
    /** Moteur de lecture préféré : auto | vlc | mpv (repli si indisponible). */
    val playerEngine = StringPref("play.engine", "auto")
    /** Normalisation du volume (replay-gain libvlc) : none | track | album. */
    val audioNormalization = StringPref("audio.normalization", "none")
    // Consommés par le profil de capacités envoyé au serveur (L5).
    val preferFmp4 = BoolPref("adv.fmp4", true)
    val enableDts = BoolPref("adv.dts", false)
    val enableTrueHd = BoolPref("adv.truehd", false)
    val preferredVideoCodec = StringPref("adv.videocodec", "auto")
    val preferredAudioCodec = StringPref("adv.audiocodec", "auto")

    // --- Affichage : écran de veille + vignettes ---------------------------
    val screensaverEnabled = BoolPref("display.screensaver", false)
    val screensaverDelayMin = IntPref("display.screensaverdelay", 3)
    /** Vignette d'épisode (true) ou affiche de la série dans À suivre/Reprendre. */
    val useEpisodeImages = BoolPref("display.episodeimages", true)

    // --- Streaming (moteur torrent) — équivalent de la page Stremio --------
    /** Taille max du cache torrent, en Gio. */
    val torrentCacheGib = IntPref("stream.cachegib", 10)
    /** Profil de torrent : default | fast | ram — arbitre débit et écriture disque. */
    val torrentProfile = StringPref("stream.profile", "default")
    /** Dossier du cache torrent ; vide = emplacement par défaut. */
    val torrentCacheDir = StringPref("stream.cachedir", "")
    /** Dossier des téléchargements ; vide = ~/Téléchargements. */
    val downloadDir = StringPref("stream.downloaddir", "")
    /** Profil de transcodage : « none » ou un périphérique détecté (vaapi-renderD128…). */
    val transcodeProfile = StringPref("stream.transcode", "none")

    // --- Segments de média (actions, comme Jellyfin) -----------------------
    // Valeurs : none | ask | auto. Consommés par le lecteur quand le serveur
    // fournit des segments (plugin) — déjà stockés et modifiables.
    val segmentIntro = StringPref("segments.intro", "ask")
    val segmentOutro = StringPref("segments.outro", "ask")
    val segmentRecap = StringPref("segments.recap", "none")
    val segmentPreview = StringPref("segments.preview", "none")
    val segmentCommercial = StringPref("segments.commercial", "auto")

    // --- Qualité & réseau --------------------------------------------------
    /** Plafond de bitrate par défaut du lecteur ; 0 = automatique. */
    val defaultMaxBitrate = LongPref("quality.maxbitrate", 0L)

    // --- Audio & sous-titres -----------------------------------------------
    /** auto | fr | en — pré-sélection de la piste audio au lancement. */
    val preferredAudioLang = StringPref("audio.lang", "auto")
    /** off | fr | en — pré-sélection des sous-titres au lancement. */
    val preferredSubLang = StringPref("subs.lang", "off")
}
