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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.domain.PlayRequest
import app.lumen.domain.PrivateStorageConfig
import app.lumen.domain.S3Client
import app.lumen.domain.S3Entry
import app.lumen.domain.StorageConfigCodec
import app.lumen.domain.StorageSource
import app.lumen.domain.StorageSourceRepository
import app.lumen.ui.components.QrCodeView
import app.lumen.ui.theme.LumenColors
import kotlinx.coroutines.launch

/**
 * Stockage S3-compatible perso (S3/R2/B2) : juste une source distante de plus,
 * au même titre qu'un serveur Jellyfin. Export/import via QR — pour SES
 * propres appareils, ou à transmettre à quelqu'un qu'on connaît déjà.
 * Un clic sur une source ouvre la navigation ; la lecture passe par une URL
 * signée temporaire, lue en HTTPS direct.
 */
@Composable
fun StorageSourcesSection(
    repo: StorageSourceRepository,
    bucketRepo: app.lumen.domain.BucketLibraryRepository,
    tmdb: app.lumen.api.TmdbClient,
    onPlay: (PlayRequest) -> Unit,
    onLibraryChanged: () -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var sources by remember { mutableStateOf(repo.list()) }
    var showAdd by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var qrFor by remember { mutableStateOf<StorageSource?>(null) }
    var browsing by remember { mutableStateOf<StorageSource?>(null) }
    var indexing by remember { mutableStateOf<String?>(null) }          // id en cours
    var indexResult by remember { mutableStateOf<Pair<String, String>?>(null) }  // id → message

    Text(
        "Un bucket S3, R2 ou B2 personnel. Lumen s'y connecte comme à un serveur " +
            "de plus — rien n'est partagé automatiquement.",
        color = LumenColors.Muted, fontSize = 13.sp,
    )

    sources.forEach { s ->
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(LumenColors.Surface, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { browsing = s },
                ) {
                    Text(s.config.label, color = LumenColors.OnBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "${s.config.kind.uppercase()} · ${s.config.bucket} · " +
                            if (s.config.folders.isEmpty()) {
                                "tout le bucket"
                            } else {
                                s.config.folders.joinToString(", ") { it.trimEnd('/') }
                            },
                        color = LumenColors.Muted, fontSize = 11.sp,
                    )
                }
                if (indexing == s.id) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = LumenColors.Accent, modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(
                        "Indexer",
                        color = LumenColors.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            indexing = s.id
                            indexResult = null
                            scope.launch {
                                val result = app.lumen.domain.BucketIndexer.index(
                                    s, app.lumen.domain.S3Client(), tmdb, bucketRepo,
                                )
                                indexResult = s.id to result.fold(
                                    onSuccess = { n -> "$n titre${if (n > 1) "s" else ""} sur l'accueil" },
                                    onFailure = { "Échec : ${it.message}" },
                                )
                                indexing = null
                                if (result.isSuccess) onLibraryChanged()
                            }
                        },
                    )
                }
                Spacer(Modifier.width(14.dp))
                Icon(
                    Icons.Filled.QrCode,
                    contentDescription = "Exporter en QR code",
                    tint = LumenColors.Muted,
                    modifier = Modifier.size(18.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { qrFor = s },
                )
                Spacer(Modifier.width(14.dp))
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Retirer",
                    tint = LumenColors.Muted,
                    modifier = Modifier.size(18.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        repo.remove(s.id)
                        bucketRepo.removeForSource(s.id)
                        sources = repo.list()
                        if (browsing?.id == s.id) browsing = null
                        onLibraryChanged()
                    },
                )
            }
            indexResult?.takeIf { it.first == s.id }?.let { (_, message) ->
                Text(
                    message,
                    color = if (message.startsWith("Échec")) LumenColors.Accent else LumenColors.Muted,
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
        ) { showAdd = true }.padding(vertical = 6.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = LumenColors.Accent, modifier = Modifier.size(20.dp))
        Text("Ajouter un bucket", color = LumenColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { showImport = true }.padding(vertical = 6.dp),
    ) {
        Icon(Icons.Filled.QrCode, contentDescription = null, tint = LumenColors.Accent, modifier = Modifier.size(20.dp))
        Text("Importer un code", color = LumenColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }

    if (showAdd) {
        AddBucketDialog(
            onDismiss = { showAdd = false },
            onSave = { cfg -> repo.add(cfg); sources = repo.list(); showAdd = false },
        )
    }
    if (showImport) {
        ImportBucketDialog(
            onDismiss = { showImport = false },
            onImport = { cfg -> repo.add(cfg); sources = repo.list(); showImport = false },
        )
    }
    qrFor?.let { s ->
        QrExportDialog(config = s.config, onDismiss = { qrFor = null })
    }
    browsing?.let { s ->
        S3BrowserDialog(config = s.config, onPlay = onPlay, onDismiss = { browsing = null })
    }
}

@Composable
private fun S3BrowserDialog(config: PrivateStorageConfig, onPlay: (PlayRequest) -> Unit, onDismiss: () -> Unit) {
    val client = remember { S3Client() }
    var prefix by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<S3Entry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(prefix) {
        loading = true
        error = null
        client.list(config, prefix).fold(
            onSuccess = { entries = it },
            onFailure = { error = it.message ?: "Échec de connexion" },
        )
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LumenColors.Surface,
        title = { Text(config.label, color = LumenColors.OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (prefix.isNotEmpty()) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Remonter",
                            tint = LumenColors.Accent,
                            modifier = Modifier.size(18.dp).clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                prefix = prefix.trimEnd('/').substringBeforeLast('/', "")
                                    .let { if (it.isEmpty()) "" else "$it/" }
                            },
                        )
                    }
                    Text("/${prefix}", color = LumenColors.Muted, fontSize = 11.sp, maxLines = 1)
                }
                when {
                    loading -> androidx.compose.material3.CircularProgressIndicator(
                        color = LumenColors.Accent, modifier = Modifier.size(24.dp),
                    )
                    error != null -> Text(error ?: "", color = LumenColors.Accent, fontSize = 13.sp)
                    entries.isEmpty() -> Text("Bucket vide", color = LumenColors.Muted, fontSize = 13.sp)
                    else -> Column {
                        entries.forEach { entry ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        if (entry.isDirectory) {
                                            prefix = entry.key
                                        } else {
                                            onPlay(
                                                PlayRequest(
                                                    url = client.presignGet(config, entry.key),
                                                    title = entry.name,
                                                ),
                                            )
                                            onDismiss()
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                            ) {
                                Icon(
                                    if (entry.isDirectory) {
                                        Icons.Filled.Folder
                                    } else {
                                        Icons.Filled.InsertDriveFile
                                    },
                                    contentDescription = null,
                                    tint = LumenColors.Muted,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(entry.name, color = LumenColors.OnBackground, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Text(
                "Fermer", color = LumenColors.Accent,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ).padding(8.dp),
            )
        },
    )
}

@Composable
private fun QrExportDialog(config: PrivateStorageConfig, onDismiss: () -> Unit) {
    val code = remember(config) { StorageConfigCodec.export(config) }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LumenColors.Surface,
        title = { Text(config.label, color = LumenColors.OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Ce QR contient la clé d'accès en clair — ne le montre qu'à " +
                        "quelqu'un en qui tu as confiance, comme un mot de passe Wi-Fi.",
                    color = LumenColors.Muted, fontSize = 12.sp,
                )
                QrCodeView(code, modifier = Modifier.widthIn(max = 260.dp))
            }
        },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { clipboard.setText(AnnotatedString(code)) }.padding(8.dp),
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = LumenColors.Accent, modifier = Modifier.size(16.dp))
                Text("Copier le code", color = LumenColors.Accent)
            }
        },
        dismissButton = {
            Text(
                "Fermer", color = LumenColors.Muted,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ).padding(8.dp),
            )
        },
    )
}

@Composable
private fun ImportBucketDialog(onDismiss: () -> Unit, onImport: (PrivateStorageConfig) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LumenColors.Surface,
        title = { Text("Importer un code", color = LumenColors.OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Colle le code reçu (exporté depuis un autre appareil).", color = LumenColors.Muted, fontSize = 12.sp)
                DialogField(value = text, onChange = { text = it; error = null })
                error?.let { Text(it, color = LumenColors.Accent, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Text(
                "Importer", color = LumenColors.Accent, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    val cfg = StorageConfigCodec.import(text)
                    if (cfg == null) error = "Code invalide ou corrompu" else onImport(cfg)
                }.padding(8.dp),
            )
        },
        dismissButton = {
            Text(
                "Annuler", color = LumenColors.Muted,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ).padding(8.dp),
            )
        },
    )
}

/**
 * Ajout d'un bucket en DEUX temps : identifiants (avec test de connexion),
 * puis choix des dossiers à indexer. Sans ce second temps, l'indexeur
 * ratisserait le bucket entier — y compris ce qui n'est pas de la vidéo.
 */
@Composable
private fun AddBucketDialog(onDismiss: () -> Unit, onSave: (PrivateStorageConfig) -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var step by remember { mutableStateOf(1) }
    var label by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("s3") }
    var endpoint by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var bucket by remember { mutableStateOf("") }
    var accessKey by remember { mutableStateOf("") }
    var secretKey by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Result<Int>?>(null) }
    var fixes by remember { mutableStateOf<List<String>>(emptyList()) }

    // Étape 2 : navigation dans le bucket et cases à cocher.
    val client = remember { S3Client() }
    var prefix by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<S3Entry>>(emptyList()) }
    var browsing by remember { mutableStateOf(false) }
    var browseError by remember { mutableStateOf<String?>(null) }
    var chosen by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun config() = PrivateStorageConfig(
        label = label.ifBlank { bucket }, kind = kind, endpoint = endpoint,
        region = region.ifBlank { null }, bucket = bucket,
        accessKey = accessKey, secretKey = secretKey,
        folders = chosen.sorted(),
    )

    fun load(target: String) {
        browsing = true
        browseError = null
        scope.launch {
            client.list(config(), target).fold(
                onSuccess = { entries = it },
                onFailure = { browseError = it.message ?: "Lecture impossible" },
            )
            browsing = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LumenColors.Surface,
        title = {
            Text(
                if (step == 1) "Ajouter un bucket" else "Dossiers à indexer",
                color = LumenColors.OnBackground,
            )
        },
        text = {
            if (step == 1) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("s3", "r2", "b2").forEach { k ->
                            Text(
                                k.uppercase(),
                                color = if (kind == k) LumenColors.Accent else LumenColors.Muted,
                                fontSize = 13.sp,
                                fontWeight = if (kind == k) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .background(if (kind == k) LumenColors.SurfaceHigh else LumenColors.Surface, RoundedCornerShape(6.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { kind = k }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                    // Chaque fournisseur nomme ces champs différemment : c'est
                    // la première cause de blocage à la configuration.
                    ProviderHelp(kind)
                    DialogField("Nom", label) { label = it }
                    DialogField(endpointHint(kind), endpoint) { endpoint = it; testResult = null; fixes = emptyList() }
                    DialogField(regionHint(kind), region) { region = it; testResult = null; fixes = emptyList() }
                    DialogField("Bucket", bucket) { bucket = it; testResult = null; fixes = emptyList() }
                    DialogField(accessHint(kind), accessKey) { accessKey = it; testResult = null; fixes = emptyList() }
                    DialogField(secretHint(kind), secretKey, password = true) { secretKey = it; testResult = null; fixes = emptyList() }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.clickable(
                            enabled = !testing && endpoint.isNotBlank() && bucket.isNotBlank(),
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            // Remettre la saisie d'aplomb AVANT d'interroger le
                            // fournisseur : la moitié des échecs sont un collage
                            // de travers, pas une mauvaise clé.
                            val tidy = tidyUp(endpoint, region, bucket, accessKey, secretKey, kind)
                            endpoint = tidy.endpoint
                            region = tidy.region
                            bucket = tidy.bucket
                            accessKey = tidy.accessKey
                            secretKey = tidy.secretKey
                            kind = tidy.kind
                            fixes = tidy.notes
                            testing = true
                            testResult = null
                            scope.launch {
                                testResult = client.list(config(), "").map { it.size }
                                testing = false
                            }
                        }.padding(vertical = 4.dp),
                    ) {
                        if (testing) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = LumenColors.Accent, modifier = Modifier.size(14.dp),
                            )
                        } else {
                            Text("Tester la connexion", color = LumenColors.Accent, fontSize = 13.sp)
                        }
                        testResult?.fold(
                            onSuccess = {
                                Text(
                                    "✓ connecté — $it élément${if (it > 1) "s" else ""} à la racine",
                                    color = androidx.compose.ui.graphics.Color(0xFF3ECF6B), fontSize = 12.sp,
                                )
                            },
                            onFailure = {
                                Text("✗ ${it.message ?: "échec"}", color = LumenColors.Accent, fontSize = 12.sp)
                            },
                        )
                    }
                    // Dire ce qui a été rectifié : une correction silencieuse
                    // donnerait l'impression que la saisie était bonne.
                    fixes.forEach {
                        Text("↻ $it", color = LumenColors.Muted, fontSize = 11.sp)
                    }
                    // Le message brut du fournisseur ne dit jamais quoi corriger.
                    testResult?.exceptionOrNull()?.let { error ->
                        failureHint(error.message.orEmpty(), endpoint, accessKey)?.let {
                            Text(it, color = LumenColors.Muted, fontSize = 11.sp)
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Coche les dossiers qui contiennent tes films, séries ou " +
                            "dossiers HLS. Tu peux en choisir plusieurs. Rien de coché = " +
                            "tout le bucket sera parcouru.",
                        color = LumenColors.Muted, fontSize = 12.sp,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (prefix.isNotEmpty()) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = "Remonter",
                                tint = LumenColors.Accent,
                                modifier = Modifier.size(18.dp).clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    val up = prefix.trimEnd('/').substringBeforeLast('/', "")
                                    prefix = if (up.isEmpty()) "" else "$up/"
                                    load(prefix)
                                },
                            )
                        }
                        Text("/$prefix", color = LumenColors.Muted, fontSize = 11.sp, maxLines = 1)
                    }
                    when {
                        browsing -> androidx.compose.material3.CircularProgressIndicator(
                            color = LumenColors.Accent, modifier = Modifier.size(22.dp),
                        )
                        browseError != null -> Text(browseError ?: "", color = LumenColors.Accent, fontSize = 12.sp)
                        entries.none { it.isDirectory } && prefix.isEmpty() ->
                            Text(
                                "Aucun dossier à la racine — les fichiers seront indexés tels quels.",
                                color = LumenColors.Muted, fontSize = 12.sp,
                            )
                        else -> Column {
                            entries.filter { it.isDirectory }.forEach { dir ->
                                val picked = dir.key in chosen
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                ) {
                                    Icon(
                                        if (picked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                        contentDescription = null,
                                        tint = if (picked) LumenColors.Accent else LumenColors.Muted,
                                        modifier = Modifier.size(20.dp).clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            chosen = if (picked) chosen - dir.key else chosen + dir.key
                                        },
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        dir.name,
                                        color = LumenColors.OnBackground,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f).clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { prefix = dir.key; load(dir.key) },
                                    )
                                    Icon(
                                        Icons.Filled.Folder,
                                        contentDescription = "Ouvrir",
                                        tint = LumenColors.Muted,
                                        modifier = Modifier.size(16.dp).clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { prefix = dir.key; load(dir.key) },
                                    )
                                }
                            }
                        }
                    }
                    if (chosen.isNotEmpty()) {
                        Text(
                            "Sélection : " + chosen.sorted().joinToString(", ") { it.trimEnd('/') },
                            color = LumenColors.Accent, fontSize = 11.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (step == 1) {
                val ok = testResult?.isSuccess == true && label.isNotBlank()
                Text(
                    "SUIVANT",
                    color = if (ok) LumenColors.Accent else LumenColors.Muted,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(
                        enabled = ok,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { step = 2; load("") }.padding(8.dp),
                )
            } else {
                Text(
                    "Enregistrer",
                    color = LumenColors.Accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSave(config()) }.padding(8.dp),
                )
            }
        },
        dismissButton = {
            Text(
                if (step == 1) "Annuler" else "Retour",
                color = LumenColors.Muted,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { if (step == 1) onDismiss() else step = 1 }.padding(8.dp),
            )
        },
    )
}

/** Champ de formulaire commun aux dialogues Sources — texte simple ou masqué. */
@Composable
fun DialogField(label: String? = null, value: String, password: Boolean = false, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        label?.let { Text(it, color = LumenColors.Muted, fontSize = 11.sp) }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            visualTransformation = if (password) {
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
            textStyle = TextStyle(color = LumenColors.OnBackground, fontSize = 13.sp),
            cursorBrush = SolidColor(LumenColors.Accent),
            modifier = Modifier.fillMaxWidth()
                .background(LumenColors.SurfaceHigh, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

// --- Aide à la configuration -------------------------------------------------
//
// Aucun fournisseur n'emploie le vocabulaire d'Amazon. Sur Backblaze on parle de
// « keyID » et « applicationKey », sur Cloudflare de « jeton d'API R2 » : sans
// repères, on cherche des champs qui n'existent pas sous ce nom.

private fun endpointHint(kind: String): String = when (kind) {
    "b2" -> "Endpoint — visible sur la page du bucket, ligne « Endpoint »"
    "r2" -> "Endpoint — https://<id-de-compte>.r2.cloudflarestorage.com"
    else -> "Endpoint (https://…)"
}

private fun regionHint(kind: String): String = when (kind) {
    "b2" -> "Région — celle contenue dans l'endpoint, ex. us-west-004"
    "r2" -> "Région — laisser « auto »"
    else -> "Région (optionnel)"
}

private fun accessHint(kind: String): String = when (kind) {
    "b2" -> "Access key = le « keyID » de ta clé d'application"
    "r2" -> "Access key = « Access Key ID » du jeton R2"
    else -> "Access key"
}

private fun secretHint(kind: String): String = when (kind) {
    "b2" -> "Secret key = la « applicationKey », affichée UNE seule fois"
    "r2" -> "Secret key = « Secret Access Key » du jeton R2"
    else -> "Secret key"
}

// --- Redressement de la saisie ----------------------------------------------

internal class Tidy(
    val endpoint: String, val region: String, val bucket: String,
    val accessKey: String, val secretKey: String, val kind: String,
    val notes: List<String>,
)

/**
 * Rectifie ce qui peut l'être avant d'interroger le fournisseur.
 *
 * Une clé se copie-colle depuis une page web, et arrive rarement propre :
 * espaces, adresse sans `https://`, chemin du bucket collé à l'endpoint,
 * région laissée vide alors qu'elle est lisible dans l'adresse, et surtout les
 * deux clés interverties — chez tous les fournisseurs, celle qui identifie et
 * celle qui signe se ressemblent assez pour qu'on s'y trompe.
 *
 * Chaque correction est annoncée : rectifier en silence laisserait croire que
 * la saisie était bonne, et la même erreur reviendrait à l'appareil suivant.
 */
internal fun tidyUp(
    endpoint: String, region: String, bucket: String,
    accessKey: String, secretKey: String, kind: String,
): Tidy {
    val notes = mutableListOf<String>()

    // Un collage traîne souvent une espace ou un retour à la ligne, invisibles
    // dans le champ mais fatals à la signature.
    var ep = endpoint.trim()
    var rg = region.trim()
    var bk = bucket.trim()
    var ak = accessKey.trim()
    var sk = secretKey.trim()
    if (listOf(endpoint, region, bucket, accessKey, secretKey) != listOf(ep, rg, bk, ak, sk)) {
        notes += "Espaces superflus retirés."
    }

    if (ep.isNotEmpty() && !ep.startsWith("http", ignoreCase = true)) {
        ep = "https://$ep"
        notes += "Adresse complétée en https://."
    }

    // Endpoint collé avec le chemin du bucket : on sépare les deux, et le nom
    // récupéré sert si le champ Bucket est encore vide.
    val afterScheme = ep.substringAfter("://", "")
    if (afterScheme.contains('/')) {
        val path = afterScheme.substringAfter('/').trim('/')
        ep = ep.substringBefore("://") + "://" + afterScheme.substringBefore('/')
        if (path.isNotEmpty()) {
            if (bk.isEmpty()) {
                bk = path.substringBefore('/')
                notes += "Bucket « $bk » repris de l'adresse."
            } else {
                notes += "Chemin retiré de l'adresse."
            }
        }
    }

    val host = ep.substringAfter("://", "").lowercase()

    // L'onglet choisi n'a aucun effet sur la requête, mais il commande les
    // libellés et l'aide : le caler sur l'adresse évite de lire le mode d'emploi
    // d'Amazon en configurant du Backblaze.
    val detected = when {
        host.endsWith("backblazeb2.com") -> "b2"
        host.endsWith("r2.cloudflarestorage.com") -> "r2"
        host.endsWith("amazonaws.com") -> "s3"
        else -> kind
    }
    if (detected != kind) notes += "Fournisseur reconnu : ${detected.uppercase()}."

    if (rg.isEmpty()) {
        // Les adresses de la forme s3.<région>.<fournisseur> portent la région.
        val parts = host.split('.')
        val guessed = when {
            detected == "r2" -> "auto"
            parts.size >= 3 && parts[0].startsWith("s3") -> parts[1]
            else -> ""
        }
        if (guessed.isNotEmpty()) {
            rg = guessed
            notes += "Région déduite de l'adresse : $guessed."
        }
    }

    // Interversion des deux clés. Chaque fournisseur a une forme reconnaissable :
    // Backblaze préfixe son secret d'un K, Cloudflare donne un secret deux fois
    // plus long que l'identifiant. On ne permute que si la forme est nette dans
    // les deux champs à la fois — sinon on laisse la saisie intacte.
    val swapped = when {
        ak.startsWith("K00") && sk.startsWith("00") -> true
        ak.length == 64 && sk.length == 32 -> true
        else -> false
    }
    if (swapped) {
        val keep = ak
        ak = sk
        sk = keep
        notes += "Access key et Secret key étaient inversées : remises dans l'ordre."
    }

    return Tidy(ep, rg, bk, ak, sk, detected, notes)
}

/**
 * Traduit l'erreur renvoyée par le fournisseur en geste à faire.
 *
 * Backblaze mérite un cas à part : sa clé maîtresse (l'identifiant de compte,
 * douze caractères) est refusée par l'API S3, qui exige une clé d'application.
 * Le serveur répond « Malformed Access Key Id » — rien n'indique que la clé est
 * simplement du mauvais type.
 */
private fun failureHint(message: String, endpoint: String, accessKey: String): String? {
    val backblaze = endpoint.contains("backblazeb2.com", ignoreCase = true)
    // Un identifiant de compte Backblaze fait exactement douze caractères ; une
    // clé d'application en fait vingt-cinq. La longueur suffit à trancher.
    val looksLikeAccountId = accessKey.length == 12
    // Sur Backblaze le secret commence par « K » suivi de la version de l'API :
    // le voir dans le champ Access key signifie que les deux ont été échangés.
    val fieldsSwapped = accessKey.startsWith("K00")
    return when {
        fieldsSwapped ->
            "Les deux champs semblent inversés : l'Access key doit être le " +
                "« keyID » (25 caractères, commençant par 005), et la Secret key " +
                "l'« applicationKey » (celle qui commence par K)."
        message.contains("Malformed Access Key", ignoreCase = true) &&
            (backblaze || looksLikeAccountId) ->
            "Backblaze refuse la clé maîtresse sur son API S3. L'Access key doit " +
                "être le « keyID » d'une clé d'application (Account → Application " +
                "Keys → Add a New Application Key), pas l'identifiant de compte " +
                "de douze caractères."
        message.contains("Malformed Access Key", ignoreCase = true) ->
            "L'Access key n'a pas le format attendu par ce fournisseur — vérifie " +
                "que c'est bien l'identifiant de clé, et non un identifiant de compte."
        message.contains("SignatureDoesNotMatch", ignoreCase = true) ->
            "L'Access key est reconnue, mais la Secret key ne correspond pas. Sur " +
                "la plupart des fournisseurs elle n'est affichée qu'une fois : " +
                "regénère une clé si tu ne l'as plus."
        message.contains("403") && backblaze ->
            "La clé existe mais n'a pas accès à ce bucket : à la création d'une " +
                "clé d'application, choisis le bucket voulu ou « All »."
        message.contains("NoSuchBucket", ignoreCase = true) ->
            "Le bucket n'existe pas sous ce nom à cette adresse — vérifie le nom " +
                "et que l'endpoint est celui de la bonne région."
        else -> null
    }
}

@Composable
private fun ProviderHelp(kind: String) {
    val steps = when (kind) {
        "b2" -> listOf(
            "Backblaze appelle ces champs autrement : va dans « Account » → " +
                "« Application Keys », puis « Add a New Application Key ».",
            "Le « keyID » obtenu est l'Access key ; la « applicationKey » est la " +
                "Secret key — elle n'est montrée qu'une fois, note-la tout de suite.",
            "L'endpoint et la région se lisent sur la page du bucket, ligne " +
                "« Endpoint » (par exemple s3.us-west-004.backblazeb2.com).",
            "Donne à la clé l'accès au bucket voulu, en lecture au minimum.",
        )
        "r2" -> listOf(
            "Dans le tableau de bord Cloudflare : R2 → « Manage R2 API Tokens » " +
                "→ « Create API token ».",
            "Le jeton fournit un « Access Key ID » et un « Secret Access Key » — " +
                "ce sont les deux champs ci-dessous.",
            "L'endpoint est affiché avec le jeton : https://<id-de-compte>." +
                "r2.cloudflarestorage.com. La région reste « auto ».",
        )
        else -> listOf(
            "Sur Amazon S3 : console IAM → ton utilisateur → « Security " +
                "credentials » → « Create access key ».",
            "L'endpoint suit la forme https://s3.<région>.amazonaws.com.",
            "Sur un autre fournisseur compatible (Wasabi, Scaleway, MinIO…), " +
                "cherche « clés d'accès S3 » ou « S3 credentials ».",
        )
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
            .background(LumenColors.SurfaceHigh, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Text("Où trouver ces informations", color = LumenColors.OnBackground, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        steps.forEach { Text("• $it", color = LumenColors.Muted, fontSize = 11.sp) }
    }
}
