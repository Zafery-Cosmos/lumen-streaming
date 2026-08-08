package app.lumen

/** Nom lisible de l'appareil, montré dans « Appareils » du tableau de bord Jellyfin. */
expect fun platformDeviceName(): String

/** Télécharge une URL dans le dossier de téléchargements ; false si non supporté. */
expect fun platformDownload(url: String, fileName: String): Boolean
