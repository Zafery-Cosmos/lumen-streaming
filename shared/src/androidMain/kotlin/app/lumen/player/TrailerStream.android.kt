package app.lumen.player

// Android n'a ni yt-dlp ni le script youtube.lua de libvlc : la résolution
// demandera un extracteur embarqué (NewPipeExtractor). Tant qu'il n'est pas
// là, on le dit au lieu de lancer une lecture qui échouerait.
actual suspend fun resolveYouTubeStream(videoId: String): TrailerStream? = null
