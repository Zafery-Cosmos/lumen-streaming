package app.lumen.domain

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Connexion à un stockage S3-compatible perso (S3, R2, B2…) — Lumen n'y voit
 * qu'une source distante de plus, au même titre qu'un serveur Jellyfin ou
 * Plex. Rien n'est partagé automatiquement : l'export/import ci-dessous sert
 * à synchroniser SES PROPRES appareils, ou à transmettre à une personne dont
 * on connaît déjà l'identité — jamais à découvrir ou inviter qui que ce soit.
 */
@Serializable
data class PrivateStorageConfig(
    val label: String,
    val kind: String,       // s3 | r2 | b2
    val endpoint: String,
    val region: String? = null,
    val bucket: String,
    val accessKey: String,
    val secretKey: String,
    /**
     * Dossiers à indexer (préfixes, « Films/ », « Series/ »…). Vide = tout le
     * bucket. Valeur par défaut : les anciens QR codes, qui n'ont pas ce champ,
     * restent lisibles.
     */
    val folders: List<String> = emptyList(),
)

/** Compression/décompression GZip — pas de multiplateforme pur en stdlib. */
expect fun gzipCompress(bytes: ByteArray): ByteArray
expect fun gzipDecompress(bytes: ByteArray): ByteArray

/**
 * Export/import d'une configuration de stockage sous forme de chaîne
 * partageable par QR code ou capture d'écran.
 *
 * IMPORTANT — ceci est un ENCODAGE, pas un CHIFFREMENT : GZip et Base64
 * transportent les données, ils ne les protègent pas. Quiconque décode la
 * chaîne lit la clé d'accès en clair, exactement comme un QR de mot de passe
 * Wi-Fi. La sécurité vient de qui on choisit de la montrer, pas du format.
 *
 * La sortie est déterministe (pas d'horodatage, pas de sel) : exporter deux
 * fois la même config produit exactement la même chaîne.
 */
object StorageConfigCodec {
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalEncodingApi::class)
    fun export(config: PrivateStorageConfig): String {
        val payload = json.encodeToString(config).encodeToByteArray()
        return Base64.UrlSafe.encode(gzipCompress(payload))
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun import(encoded: String): PrivateStorageConfig? = runCatching {
        val payload = gzipDecompress(Base64.UrlSafe.decode(encoded.trim()))
        json.decodeFromString<PrivateStorageConfig>(payload.decodeToString())
    }.getOrNull()
}
