import type { Metadata } from "next";
import PageHeader from "@/components/PageHeader";

export const metadata: Metadata = { title: "Fonctionnalités" };

type Feature = { title: string; body: string };
type Section = { heading: string; intro?: string; items: Feature[] };

const SECTIONS: Section[] = [
  {
    heading: "Accueil éditorial",
    items: [
      { title: "Carrousel plein écran", body: "Hero mis en avant, rangées éditoriales (Top 10, genres, Ma médiathèque) à la place d'une grille de dossiers." },
      { title: "Métadonnées complétées par TMDB", body: "Titres, résumés, vignettes et logos récupérés automatiquement quand le serveur n'en fournit pas." },
      { title: "Séries réorganisées", body: "Les séries en numérotation absolue sont réorganisées en vraies saisons, à partir de TMDB." },
      { title: "Fiches films, séries et acteurs", body: "Pages dédiées avec casting, œuvres liées et navigation croisée." },
    ],
  },
  {
    heading: "Toutes les sources, une seule interface",
    intro: "L'utilisateur voit des titres et un bouton « Lire ». Derrière, Lumen sait parler à quatre types de sources différentes.",
    items: [
      { title: "Jellyfin", body: "La médiathèque de référence, toujours prioritaire, avec lecture directe (Direct Play)." },
      { title: "Dossiers HLS", body: "Déjà transcodés, lus sans qu'aucun serveur ne ré-encode quoi que ce soit." },
      { title: "Addons Stremio", body: "Protocole officiel implémenté à la main : Torrentio, Frenchio, TvVoo… n'importe quel addon existant fonctionne." },
      { title: "Buckets S3 / R2 / B2", body: "Stockage objet (Amazon S3, Cloudflare R2, Backblaze B2, Wasabi, Scaleway, MinIO…). Test de connexion, choix des dossiers à indexer, lecture par URL signée." },
      { title: "WebDAV", body: "Nextcloud, Synology, mod_dav — test de connexion, navigation des dossiers, lecture directe." },
      { title: "FTP", body: "Navigation et lecture via un proxy HTTP local — aucun lecteur vidéo ne lit du FTP nativement." },
      { title: "HLS dans un bucket privé", body: "Le proxy local signe chaque segment à la volée : une simple URL signée du manifeste ne suffirait pas." },
      { title: "Assistant de connexion en deux temps", body: "Identifiants + test de connexion, puis choix des dossiers à indexer (sélection multiple, navigation dans les sous-dossiers)." },
      { title: "Redressement automatique de la saisie", body: "Espaces superflus, adresse sans https://, chemin collé au bucket, région déduite de l'adresse, clés d'accès et secrètes interverties : corrigés avant même d'interroger le fournisseur, et chaque correction est annoncée." },
      { title: "Aide contextuelle par fournisseur", body: "Chaque fournisseur nomme ses clés autrement (keyID/applicationKey chez Backblaze, jeton d'API chez Cloudflare) : l'endroit exact où les trouver est expliqué dans l'app." },
      { title: "Export / import par QR code", body: "Une source se transfère vers un autre appareil en scannant un QR code — sans retaper la moindre clé d'accès." },
    ],
  },
  {
    heading: "Envoi vers le serveur",
    items: [
      { title: "Upload SFTP / FTP", body: "Un dossier HLS local s'envoie directement sur le serveur de médias, avec suivi de progression et reprise sur coupure." },
      { title: "Lecture via une URL locale", body: "Jellyfin n'a pas d'API d'upload : Lumen dépose un fichier-pointeur que Jellyfin indexe comme un film, et sert le contenu réel via une adresse locale plutôt qu'un chemin file://." },
    ],
  },
  {
    heading: "Un vrai moteur torrent, sans debrid",
    items: [
      { title: "Lecture pendant le téléchargement", body: "Comme Stremio : un clic sur une source Torrentio lance la lecture immédiatement, pendant que le téléchargement continue." },
      { title: "Statistiques réelles", body: "Panneau affichant les pairs connectés, le débit et la progression réelle du téléchargement." },
      { title: "Aucun compte requis", body: "Un compte debrid n'est pas nécessaire — il rend simplement le démarrage instantané." },
    ],
  },
  {
    heading: "Lecteur maison",
    items: [
      { title: "Direct Play + transcodage à la demande", body: "Le flux d'origine est utilisé par défaut ; des boutons permettent de basculer vers le transcodage si besoin, avec un plafond de débit en saisie libre." },
      { title: "En-têtes HTTP côté client", body: "Beaucoup de flux HLS exigent un Referer ou un User-Agent précis. Les navigateurs interdisent de les définir ; en natif, Lumen les envoie directement, jusqu'aux segments et aux clés de chiffrement." },
      { title: "Contrôles complets", body: "Vitesse de lecture, sélection de piste, rendu vidéo, capture d'image, statistiques de flux en direct." },
      { title: "Rendu par callback (bureau)", body: "Sur Linux, une surface vidéo classique passerait au-dessus de l'interface : libVLC décode en mémoire et l'interface dessine par-dessus, contrôles compris." },
    ],
  },
  {
    heading: "Profils du foyer",
    items: [
      { title: "Reprise de lecture séparée", body: "Bibliothèque partagée, mais chaque profil garde sa propre progression sur chaque titre." },
      { title: "Code PIN", body: "Stocké haché, jamais en clair." },
      { title: "Profil enfant", body: "Masque réellement le contenu au-dessus de la limite d'âge — et le non classifié, par prudence." },
      { title: "1 757 avatars", body: "Triés par film et par série, plutôt qu'un import d'image générique." },
    ],
  },
  {
    heading: "Identifiants chiffrés",
    intro: "Détaillé sur la page Sécurité — résumé ici.",
    items: [
      { title: "Conteneur .lmn", body: "AES-256-GCM, nonce unique à chaque écriture, en-tête authentifié : modifier le fichier rend le contenu illisible plutôt que d'ouvrir une brèche." },
      { title: "Clé détenue par le système", body: "Android Keystore, avec StrongBox quand l'appareil en a une. La clé ne sort jamais de la puce." },
      { title: "Tous les identifiants concernés", body: "Jetons Jellyfin, clés d'addons, clés de buckets, mots de passe WebDAV et FTP — la base locale ne garde que des références vers le coffre." },
    ],
  },
  {
    heading: "Paramètres poussés au maximum",
    items: [
      { title: "Synchronisés avec le serveur", body: "Toute l'arborescence Jellyfin est reprise et synchronisée avec le compte serveur : modifiez dans Lumen, retrouvez-le dans le client web." },
      { title: "Noir pur OLED", body: "Un vrai noir (#000000), pas un gris foncé qui prétend l'être." },
      { title: "Sections d'accueil réordonnables", body: "L'ordre des rangées de la page d'accueil se personnalise." },
      { title: "Sauts avant/arrière séparés", body: "Deux durées indépendantes, pas un seul réglage partagé." },
      { title: "Multi-serveurs", body: "Bascule dynamique entre plusieurs serveurs Jellyfin." },
    ],
  },
  {
    heading: "Mises à jour en direct",
    items: [
      { title: "Notification instantanée", body: "Publier une version prévient toutes les apps ouvertes en direct (SSE), sans qu'elles aient à vérifier quoi que ce soit." },
      { title: "Poids annoncé avant téléchargement", body: "Le bandeau affiche la taille réelle de la mise à jour, puis le débit et le temps restant." },
      { title: "Reprise sur coupure", body: "Le téléchargement reprend après interruption grâce aux requêtes par plage (Range)." },
      { title: "PC et Android", body: "Sur PC, installation en espace utilisateur puis relance automatique. Sur Android, l'APK est transmis à l'installateur système — aucun câble, aucun ADB." },
    ],
  },
];

export default function Fonctionnalites() {
  return (
    <div className="mx-auto max-w-5xl px-6 py-16 sm:py-20">
      <PageHeader
        title="Fonctionnalités"
        lede="Tout ce que l'application sait faire aujourd'hui — pas une liste d'intentions. Ce qui n'est pas encore là est dit tout aussi clairement sur la page Technologies."
      />
      <div className="space-y-16">
        {SECTIONS.map((s) => (
          <section key={s.heading}>
            <h2 className="text-xl font-semibold text-lumen-fg">{s.heading}</h2>
            {s.intro && <p className="mt-2 max-w-2xl text-sm text-lumen-muted">{s.intro}</p>}
            <div className="mt-6 grid gap-4 sm:grid-cols-2">
              {s.items.map((f) => (
                <div key={f.title} className="rounded-lg border border-lumen-surface-high bg-lumen-surface p-5">
                  <h3 className="font-medium text-lumen-fg">{f.title}</h3>
                  <p className="mt-1.5 text-sm text-lumen-muted">{f.body}</p>
                </div>
              ))}
            </div>
          </section>
        ))}
      </div>
    </div>
  );
}
