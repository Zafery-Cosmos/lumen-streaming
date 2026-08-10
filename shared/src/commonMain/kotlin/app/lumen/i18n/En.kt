package app.lumen.i18n

/**
 * Table anglaise.
 *
 * Une clé absente ici retombe automatiquement sur le français : mieux vaut un
 * mot en français qu'une chaîne vide ou un identifiant technique à l'écran.
 */
internal val EN: Map<String, String> = mapOf(
    // --- Navigation -------------------------------------------------------
    "nav.home" to "Home",
    "nav.movies" to "Movies",
    "nav.series" to "Series",
    "nav.discover" to "Discover",
    "nav.addons" to "Add-ons",
    "nav.settings" to "Settings",
    "nav.search" to "Search",
    "nav.searchPlaceholder" to "Titles, movies, shows…",
    "nav.searchClose" to "Close search",
    "nav.sync" to "Sync",

    // --- Profile menu -----------------------------------------------------
    "profile.menu" to "Profile menu",
    "profile.profile" to "Profile",
    "profile.switch" to "Switch profile",
    "profile.logout" to "Log out",

    // --- Settings list ----------------------------------------------------
    "settings.title" to "Settings",
    "settings.display" to "Display and home",
    "settings.displaySub" to "Theme, OLED black, visible rows",
    "settings.playback" to "Playback, quality and audio",
    "settings.playbackSub" to "Resume, bitrate, languages, subtitles",
    "settings.streaming" to "Streaming and cache",
    "settings.streamingSub" to "Torrent engine, size and purge",
    "settings.simkl" to "Simkl",
    "settings.simklSub" to "Unified history, add-ons included",
    "settings.advanced" to "Advanced",
    "settings.advancedSub" to "HLS folder import, segmenting",
    "settings.quickconnect" to "Quick Connect",
    "settings.quickconnectSub" to "Authorise another device",
    "settings.service" to "Service",

    // --- Language ---------------------------------------------------------
    "lang.title" to "Interface language",
    "lang.auto" to "Automatic (system)",
    "lang.note" to "Applied immediately, no restart needed.",

    // --- Cards and context menu -------------------------------------------
    "card.play" to "Play",
    "card.playFromHere" to "Play all from here",
    "card.markWatched" to "Mark as watched",
    "card.markUnwatched" to "Mark as unwatched",
    "card.favorite" to "Favourite",
    "card.addFavorite" to "Add to favourites",
    "card.removeFavorite" to "Remove from favourites",
    "card.addToPlaylist" to "Add to playlist",
    "card.download" to "Download",
    "card.copyStreamUrl" to "Copy stream URL",
    "card.mediaInfo" to "Media info",
    "card.options" to "Options",
    "card.watched" to "Watched",
    "card.watchLater" to "Watch later",
    "card.createPlaylist" to "Create playlist",
    "card.playlistName" to "Playlist name",
    "card.createAndAdd" to "Create and add",
    "card.loading" to "Loading…",
    "card.close" to "Close",
    "card.info" to "Info",
    "card.downloadStarted" to "Download started",
    "card.downloadUnavailable" to "Not available on this platform",
    "card.urlCopied" to "URL copied (local, no token)",
    "card.addedToWatchLater" to "Added to “Watch later”",
    "card.addedTo" to "Added to “{0}”",
    "card.playlistCreated" to "Playlist “{0}” created",
    "card.failed" to "Failed",

    // --- Media info sheet --------------------------------------------------
    "info.type" to "Type",
    "info.year" to "Year",
    "info.duration" to "Runtime",
    "info.rating" to "Rating",
    "info.score" to "Score",
    "info.genres" to "Genres",
    "info.file" to "File",
    "info.id" to "ID",
    "info.minutes" to "{0} min",

    // --- Update banner -----------------------------------------------------
    "update.available" to "Update available — Lumen {0}",
    "update.installed" to "Installed version: {0}",
    "update.update" to "Update ({0})",
    "update.ignore" to "Dismiss",
    "update.later" to "Later",
    "update.installRestart" to "Install and restart",
    "update.downloadFailed" to "Download failed — try again later",
    "update.installFailed" to "Install failed — download the update again",
    "update.remaining" to "{0} remaining",

    // --- Add-ons ------------------------------------------------------------
    "addons.title" to "Add-ons",
    "addons.intro" to "Paste the manifest URL of a Stremio add-on (Torrentio, Frenchio…) — " +
        "the « stremio:// » links from install pages work too. " +
        "Its sources will show up on title pages under the « Sources » button.",
    "addons.manifestUrl" to "Manifest URL",
    "addons.install" to "Install",
    "addons.checking" to "Checking…",
    "addons.invalid" to "Invalid or unreachable manifest",
    "addons.configure" to "Configure in browser",
    "addons.remove" to "Remove",
    "addons.empty" to "No add-ons installed yet.",
)
