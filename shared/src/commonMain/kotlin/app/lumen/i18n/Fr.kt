package app.lumen.i18n

/**
 * Table française — la langue de référence.
 *
 * Les textes sont repris à l'identique de ce que l'app affichait avant
 * l'internationalisation : passer par cette table ne change rien pour un
 * utilisateur francophone.
 *
 * Les repères {0}, {1}… sont remplis par [T.format].
 */
internal val FR: Map<String, String> = mapOf(
    // --- Navigation -------------------------------------------------------
    "nav.home" to "Accueil",
    "nav.movies" to "Films",
    "nav.series" to "Séries",
    "nav.discover" to "Découvrir",
    "nav.addons" to "Addons",
    "nav.settings" to "Paramètres",
    "nav.search" to "Rechercher",
    "nav.searchPlaceholder" to "Titres, films, séries…",
    "nav.searchClose" to "Fermer la recherche",
    "nav.sync" to "Synchroniser",

    // --- Menu du profil ---------------------------------------------------
    "profile.menu" to "Menu du profil",
    "profile.profile" to "Profil",
    "profile.switch" to "Changer de profil",
    "profile.logout" to "Déconnecter",

    // --- Liste des réglages -----------------------------------------------
    "settings.title" to "Paramètres",
    "settings.display" to "Affichage et accueil",
    "settings.displaySub" to "Thème, noir OLED, rangées affichées",
    "settings.playback" to "Lecture, qualité et audio",
    "settings.playbackSub" to "Reprise, débit, langues, sous-titres",
    "settings.streaming" to "Streaming et cache",
    "settings.streamingSub" to "Moteur torrent, taille et purge",
    "settings.simkl" to "Simkl",
    "settings.simklSub" to "Historique unifié, addons compris",
    "settings.advanced" to "Avancé",
    "settings.advancedSub" to "Import de dossiers HLS, segmentation",
    "settings.quickconnect" to "Connexion rapide",
    "settings.quickconnectSub" to "Autoriser un autre appareil",
    "settings.service" to "Service",

    // --- Langue -----------------------------------------------------------
    "lang.title" to "Langue de l'interface",
    "lang.auto" to "Automatique (système)",
    "lang.note" to "Appliqué immédiatement, sans redémarrage.",

    // --- Cartes et menu contextuel ----------------------------------------
    "card.play" to "Lire",
    "card.playFromHere" to "Tout lire à partir d'ici",
    "card.markWatched" to "Marquer vu",
    "card.markUnwatched" to "Marquer non vu",
    "card.favorite" to "Favori",
    "card.addFavorite" to "Ajouter aux favoris",
    "card.removeFavorite" to "Retirer des favoris",
    "card.addToPlaylist" to "Ajouter à la liste de lecture",
    "card.download" to "Télécharger",
    "card.copyStreamUrl" to "Copier l'URL du flux",
    "card.mediaInfo" to "Informations du média",
    "card.options" to "Options",
    "card.watched" to "Vu",
    "card.watchLater" to "Regarder plus tard",
    "card.createPlaylist" to "Créer une playlist",
    "card.playlistName" to "Nom de la playlist",
    "card.createAndAdd" to "Créer et ajouter",
    "card.loading" to "Chargement…",
    "card.close" to "Fermer",
    "card.info" to "Informations",
    "card.downloadStarted" to "Téléchargement lancé",
    "card.downloadUnavailable" to "Indisponible sur cette plateforme",
    "card.urlCopied" to "URL copiée (locale, sans jeton)",
    "card.addedToWatchLater" to "Ajouté à « Regarder plus tard »",
    "card.addedTo" to "Ajouté à « {0} »",
    "card.playlistCreated" to "Playlist « {0} » créée",
    "card.failed" to "Échec",

    // --- Fiche « informations du média » ----------------------------------
    "info.type" to "Type",
    "info.year" to "Année",
    "info.duration" to "Durée",
    "info.rating" to "Classification",
    "info.score" to "Note",
    "info.genres" to "Genres",
    "info.file" to "Fichier",
    "info.id" to "Identifiant",
    "info.minutes" to "{0} min",

    // --- Bandeau de mise à jour -------------------------------------------
    "update.available" to "Mise à jour disponible — Lumen {0}",
    "update.installed" to "Version installée : {0}",
    "update.update" to "Mettre à jour ({0})",
    "update.ignore" to "Ignorer",
    "update.later" to "Plus tard",
    "update.installRestart" to "Installer et redémarrer",
    "update.downloadFailed" to "Téléchargement échoué — réessaie plus tard",
    "update.installFailed" to "Installation échouée — retélécharge la mise à jour",
    "update.remaining" to "{0} restantes",

    // --- Addons -----------------------------------------------------------
    "addons.title" to "Addons",
    "addons.intro" to "Colle l'URL du manifeste d'un addon Stremio (Torrentio, Frenchio…) — " +
        "les liens « stremio:// » des pages d'installation fonctionnent aussi. " +
        "Ses sources apparaîtront sur les fiches via le bouton « Sources ».",
    "addons.manifestUrl" to "URL du manifeste",
    "addons.install" to "Installer",
    "addons.checking" to "Vérification…",
    "addons.invalid" to "Manifeste invalide ou injoignable",
    "addons.configure" to "Configurer dans le navigateur",
    "addons.remove" to "Supprimer",
    "addons.empty" to "Aucun addon installé pour l'instant.",
)
