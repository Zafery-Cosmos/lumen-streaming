package app.lumen.player

/**
 * Un flux de bande-annonce prêt pour le moteur de lecture.
 *
 * YouTube sert ses hautes définitions en DASH : la vidéo et l'audio sont deux
 * fichiers distincts. Le moteur les recombine à la lecture (`input-slave` côté
 * libvlc) — c'est ce qui permet d'avoir du 1080p plutôt que le 360p du seul
 * flux progressif restant.
 */
data class TrailerStream(
    val videoUrl: String,
    /** Piste audio séparée (DASH) ; null si le flux est déjà multiplexé. */
    val audioUrl: String?,
    /** Qui a résolu le flux — affiché honnêtement dans le lecteur. */
    val resolvedBy: String,
)

/**
 * Transforme un identifiant de vidéo YouTube en flux lisible.
 *
 * Retourne null si la plateforme ne sait pas résoudre : l'UI le dit au lieu
 * de faire semblant.
 */
expect suspend fun resolveYouTubeStream(videoId: String): TrailerStream?
