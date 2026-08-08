package app.lumen

/** Nom lisible de l'appareil, montré dans « Appareils » du tableau de bord Jellyfin. */
expect fun platformDeviceName(): String

/** Télécharge une URL dans le dossier de téléchargements ; false si non supporté. */
expect fun platformDownload(url: String, fileName: String): Boolean

/** Ouvre une URL (http, magnet…) avec l'application système associée. */
expect fun platformOpenUrl(url: String): Boolean

/** Ouvre un sélecteur de dossier natif ; null si annulé ou indisponible. */
expect suspend fun pickDirectory(title: String): String?

/** Cherche récursivement le master.m3u8 d'un dossier ; null si absent. */
expect fun findHlsMaster(directory: String): String?

/** Lit un fichier texte local (playlists HLS). */
expect fun readLocalText(path: String): String?

/** Chemin d'un fichier voisin, relatif au master. */
expect fun resolveSibling(masterPath: String, relative: String): String

/** Nom du dossier contenant ce fichier. */
expect fun parentFolderName(path: String): String
