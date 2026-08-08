# Lumen Streaming

**Un client Jellyfin qui ressemble enfin à un service de streaming.**

Lumen est une application **native** (PC et Android) qui se branche sur votre
serveur Jellyfin existant. Elle ne remplace rien côté serveur : elle utilise son
API et son authentification, et remplace uniquement l'interface — parce qu'une
médiathèque personnelle mérite mieux qu'une grille de dossiers.

<p align="center">
  <img src="art/logo-master.png" width="160" alt="Lumen">
</p>

---

## La différence

Les clients Jellyfin officiels sont d'excellents **explorateurs de médiathèque**.
Lumen est un **service de streaming** : hero plein écran, rangées éditoriales,
lecture immédiate, et surtout la capacité de ne jamais être une impasse quand un
titre n'est pas sur le serveur.

| | Client Jellyfin classique | **Lumen** |
|---|---|---|
| **Accueil** | Grilles de bibliothèques et de dossiers | Carrousel plein écran + rangées éditoriales (Top 10, genres, Ma médiathèque) |
| **Contenu absent du serveur** | Impasse | Sources d'addons Stremio, **moteur torrent intégré** |
| **Profils** | Comptes serveur uniquement | Profils locaux du foyer : PIN, profil enfant, reprise **par profil** |
| **Avatars** | Téléverser une image soi-même | **1 757 avatars triés par film et série** |
| **Séries mal rangées** | Affichées telles quelles | **Réorganisées** via TMDB (numéros absolus → vraies saisons) |
| **Métadonnées manquantes** | Vides | Complétées par TMDB (titres, résumés, vignettes, logos) |
| **Mises à jour** | À la main | **En direct** : publier prévient les apps ouvertes instantanément |
| **Lecteur** | Contrôles de base | UI maison : vitesse, qualité, pistes, rendu, capture, statistiques de flux |

---

## Ce que ça sait faire

### Trois sources, une seule interface

L'utilisateur voit des titres et un bouton « Lire ». Derrière :

1. **Jellyfin** — la médiathèque de référence, toujours prioritaire (Direct Play).
2. **Vos dossiers HLS** — déjà transcodés, lus sans qu'aucun serveur ne ré-encode.
3. **Les addons Stremio** — Torrentio, Frenchio, TvVoo… Le protocole officiel est
   implémenté, donc **n'importe quel addon existant fonctionne**.

### Un vrai moteur torrent, sans debrid

Comme Stremio, Lumen embarque un moteur de streaming torrent : un clic sur une
source Torrentio lance la lecture **pendant le téléchargement**. Le panneau de
statistiques affiche les pairs connectés, le débit et la progression réelle.
Un compte debrid n'est pas nécessaire — il rend simplement le démarrage instantané.

### Les en-têtes côté client, sans proxy

Beaucoup de flux HLS exigent un `Referer` ou un `User-Agent` précis. Les clients
web ne peuvent pas les définir (les navigateurs l'interdisent) et doivent monter
un proxy serveur. En natif, Lumen les envoie directement — **jusqu'aux segments
et aux clés de chiffrement**, là où la plupart des implémentations échouent.

### Profils du foyer

Bibliothèque partagée, **reprise de lecture séparée**. Chaque profil a son avatar,
son éventuel code PIN (stocké haché, jamais en clair) et sa limite d'âge. Un profil
enfant masque réellement le contenu au-dessus de la limite — et le non classifié
par prudence.

### Paramètres poussés au maximum

Toute l'arborescence Jellyfin est reprise **et synchronisée avec le compte
serveur** (modifiez dans Lumen, retrouvez-le dans le client web). Plus ce que
Jellyfin n'a pas : noir pur OLED, sections d'accueil réordonnables, sauts
avant/arrière séparés, plafond de débit **en saisie libre**, apparence des
sous-titres, écran de veille, multi-serveurs à bascule dynamique.

Aucun réglage décoratif : chaque option a un effet vérifiable.

---

## Architecture

Kotlin **Compose Multiplatform** — une base de code, des lecteurs natifs.

```
lumen/
├── shared/          ~85 % du code : UI, client API, domaine, base locale
│   ├── api/         clients Jellyfin, TMDB et Stremio (écrits à la main)
│   ├── domain/      profils, réglages, réorganisation des épisodes
│   ├── player/      PlayerEngine commun + moteur torrent
│   ├── update/      mises à jour en direct (SSE)
│   └── ui/          écrans Compose et design system
├── androidApp/      Media3 / ExoPlayer
└── desktopApp/      libVLC en rendu par callback
```

| Choix | Pourquoi |
|---|---|
| Client API **écrit à la main** (Ktor) | Le SDK officiel expose 500 endpoints pour 6 % d'usage, et n'est pas pleinement multiplateforme. |
| **libVLC en rendu par callback** | Sur Linux, une surface vidéo AWT passe *au-dessus* de Compose : les contrôles seraient invisibles. libVLC décode en mémoire, Compose dessine. |
| **UI de lecteur 100 % maison** | Aucun contrôle stock : la même interface sur toutes les plateformes, derrière une interface `PlayerEngine` commune. |
| **SQLDelight** | Profils, PIN hachés et reprise par profil dans une vraie base locale. |

---

## Installation

### Prérequis

- Un serveur **Jellyfin 10.10+**
- **JDK 21** (compilation)
- SDK Android (uniquement pour l'APK)
- `libvlc` (fourni par le paquet `vlc` de votre distribution) pour le bureau

### Compiler

```bash
git clone https://github.com/Zafery-Cosmos/lumen-streaming.git
cd lumen-streaming

# Clé TMDB (métadonnées, logos, réorganisation des séries)
echo "tmdb.api.key=VOTRE_CLE" >> local.properties
echo "sdk.dir=/chemin/vers/Android/Sdk" >> local.properties   # Android seulement

./gradlew :desktopApp:packageUberJarForCurrentOS   # → desktopApp/build/compose/jars/
./gradlew :androidApp:assembleDebug                # → androidApp/build/outputs/apk/
```

Au premier lancement : adresse du serveur (la saisie est tolérante — `192.168.1.10`
suffit), puis connexion classique ou **Quick Connect**, puis création du profil.

---

## Services annexes

Deux petits services accompagnent l'app. Ils sont optionnels — l'app fonctionne
sans, avec les avatars embarqués et sans mises à jour automatiques.

### Serveur de mises à jour (`server/`)

Un fichier Python, sans dépendance :

```bash
python3 server/server.py                 # port 8500
server/publish.sh 0.3.0 "Correctif A|Correctif B" lumen.jar
```

À la publication, **toutes les apps ouvertes sont prévenues instantanément**
(SSE) : le bandeau apparaît sans redémarrage, annonce le poids avant de lancer,
puis affiche débit et temps restant. Le téléchargement reprend après coupure
(requêtes Range).

### Banque d'avatars

1 757 images triées par œuvre. `server/index-avatars.py` génère l'index JSON
consommé par l'app ; le serveur les sert et l'app ne charge que l'œuvre affichée.

```
avatars/
├── na-0001.png …            avatars neutres
└── movix/
    ├── crunchyroll/frieren/ une œuvre = un onglet
    ├── marvel/…
    └── pixar/…
```

---

## État du projet

Fonctionnel et utilisé au quotidien. Ce qui tourne : connexion et Quick Connect,
accueil éditorial, fiches films et séries, pages acteur, lecteur complet,
addons Stremio avec moteur torrent, profils, paramètres, mises à jour en direct.

Ce qui n'est pas encore là, dit franchement : le mode TV (navigation à la
télécommande), le téléchargement hors-ligne, la sélection de pistes sur Android,
et l'assistant d'import de dossiers HLS.

## Licence et avertissement

Projet personnel, publié tel quel. Lumen **ne fournit aucun contenu** : c'est un
client. Les addons Stremio sont ajoutés par l'utilisateur, qui reste responsable
de ce qu'il en fait et du respect des lois de son pays.
