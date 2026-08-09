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
| **Sources autres que Jellyfin** | Aucune | Buckets **S3/R2/B2**, **WebDAV**, **FTP** — indexés et rangés sur l'accueil |
| **Identifiants stockés** | En clair côté client | **Chiffrés AES-256-GCM**, clé tenue par le magasin de clés du système |
| **Mises à jour** | À la main | **En direct** : publier prévient les apps ouvertes instantanément, **PC et Android** |
| **Lecteur** | Contrôles de base | UI maison : vitesse, qualité, pistes, rendu, capture, statistiques de flux |

---

## Ce que ça sait faire

### Toutes vos sources, une seule interface

L'utilisateur voit des titres et un bouton « Lire ». Derrière :

1. **Jellyfin** — la médiathèque de référence, toujours prioritaire (Direct Play).
2. **Vos dossiers HLS** — déjà transcodés, lus sans qu'aucun serveur ne ré-encode.
3. **Les addons Stremio** — Torrentio, Frenchio, TvVoo… Le protocole officiel est
   implémenté, donc **n'importe quel addon existant fonctionne**.
4. **Vos stockages perso** — buckets S3, WebDAV, FTP (voir ci-dessous).

### L'onglet Service : brancher son propre stockage

Un serveur Jellyfin n'est pas la seule façon de ranger ses films. L'onglet
**Service** accepte, en plus des serveurs Jellyfin :

| Type | Ce que Lumen sait faire |
|---|---|
| **Stockage objet** (S3, R2, B2, Wasabi, Scaleway, MinIO…) | Test de connexion, choix des dossiers à indexer, lecture par **URL signée** (SigV4 maison, aucun SDK) |
| **WebDAV** (Nextcloud, Synology, mod_dav) | Test de connexion, navigation des dossiers, lecture directe |
| **FTP** | Navigation, lecture via un proxy HTTP local — aucun lecteur vidéo ne lit du FTP nativement |

**Un bucket devient une médiathèque.** L'assistant se fait en deux temps :
identifiants + « Tester la connexion », puis **choix des dossiers** à indexer
(sélection multiple, navigation dans les sous-dossiers). L'indexation repère les
fichiers vidéo et les dossiers HLS, rapproche chaque titre de **TMDB** (jaquette,
année, résumé), et les titres apparaissent sur l'accueil comme n'importe quel
film. Un dossier HLS dans un bucket privé est lu via le proxy local, qui **signe
chaque segment à la volée** — une simple URL signée du manifeste ne suffirait pas.

### Transférer une configuration par QR code

Une source de stockage s'exporte en **QR code** (JSON compressé en GZip puis
encodé en Base64 URL-safe, sortie déterministe : deux exports de la même config
donnent la même chaîne). De quoi configurer ses autres appareils sans retaper
des clés d'accès à la main.

> **Ce n'est pas du chiffrement.** GZip et Base64 transportent la donnée, ils ne
> la protègent pas : qui décode le code lit la clé d'accès en clair, exactement
> comme un QR code de mot de passe Wi-Fi. La sécurité vient de à qui vous le
> montrez. Lumen n'a **aucun annuaire, aucune mise en relation, aucun serveur**
> impliqué dans cet échange.

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

### Vos identifiants ne sont pas stockés en clair

Brancher un serveur Jellyfin, un bucket ou un partage WebDAV, c'est confier à
l'app des jetons et des clés qui ouvrent votre médiathèque. Ils sont donc rangés
chiffrés, dans un conteneur **`.lmn`** :

- **AES-256-GCM**, nonce unique à chaque écriture, en-tête authentifié — modifier
  le fichier, ne serait-ce que d'un octet, ou tenter d'y rétrograder le niveau de
  chiffrement, rend le contenu illisible plutôt que d'ouvrir une brèche.
- **Clé détenue par le système** : Android Keystore, avec **StrongBox** lorsque
  l'appareil embarque une puce dédiée. La clé n'en sort jamais — copier les
  fichiers de l'app sur un autre téléphone ne donne rien.
- **Un second niveau, à mot de passe maître** : la clé est alors dérivée à chaque
  ouverture (PBKDF2-HMAC-SHA256, 210 000 tours) et **vit uniquement en mémoire**.
  Rien sur le disque ne permet plus de déchiffrer. Le moteur est en place ;
  l'écran de saisie viendra.
- Essais d'ouverture **freinés progressivement** (jusqu'à 5 minutes d'attente, et
  le compteur survit au redémarrage), comparaisons à durée constante, clés
  effacées de la mémoire au verrouillage.
- L'appareil est **analysé** (root, débogueur attaché, émulateur, signature du
  paquet altérée) et l'utilisateur en est **informé** — sans blocage : un blocage
  se contourne, et pénaliserait surtout ceux qui maîtrisent leur matériel.

La migration depuis les versions précédentes est automatique et silencieuse :
les valeurs en clair sont reprises, chiffrées, puis effacées de leur ancien
emplacement.

**Deux choix assumés, plutôt que du théâtre de sécurité :**

*Pas d'épinglage de certificat.* Vous vous connectez à **votre** serveur, souvent
en adresse locale avec un certificat auto-signé : épingler casserait la fonction
principale de l'app pour un gain nul dans ce scénario.

*Pas d'algorithme caché.* La solidité vient de la clé, jamais du secret de la
méthode — un chiffrement qu'il faut dissimuler pour tenir ne tient pas, et sur un
projet open source il ne le resterait pas dix minutes. Le code est lisible ; c'est
ce qui permet de le vérifier.

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
│   ├── domain/      profils, réglages, réorganisation des épisodes,
│   │                clients S3/WebDAV/FTP et indexeur de bucket
│   ├── player/      PlayerEngine commun + moteur torrent + proxy de flux
│   ├── security/    coffre chiffré .lmn, clés système, contrôles d'exécution
│   ├── update/      mises à jour en direct (SSE), PC et Android
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
| **Signature S3 écrite à la main** | ~100 lignes de SigV4 remplacent un SDK de plusieurs Mo, et couvrent S3, R2, B2 et tous les compatibles d'un seul coup. |
| **Proxy de flux local** | FTP et HLS-dans-un-bucket ne se donnent à aucun lecteur vidéo tels quels : le proxy les réexpose en HTTP propre, en signant les segments à la demande. |

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
server/publish.sh 1.9.3 "Correctif A|Correctif B" lumen.jar lumen.apk
```

À la publication, **toutes les apps ouvertes sont prévenues instantanément**
(SSE) : le bandeau apparaît sans redémarrage, annonce le poids avant de lancer,
puis affiche débit et temps restant. Le téléchargement reprend après coupure
(requêtes Range). Les versions obsolètes sont purgées automatiquement du serveur.

**Sur PC**, la mise à jour s'installe en espace utilisateur puis relance l'app.
**Sur Android**, le même bandeau télécharge l'APK et passe la main à
l'installateur du système : aucun câble, aucun ADB. L'APK doit être signé avec
la même clé que l'app installée — elle est déclarée dans `local.properties`
(`lumen.keystore=…`) pour qu'un build ne puisse pas produire un paquet
impossible à installer.

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
addons Stremio avec moteur torrent, profils, paramètres, import de dossiers HLS,
stockages perso (S3/WebDAV/FTP) avec indexation, chiffrement des identifiants,
mises à jour en direct sur PC et Android.

Ce qui n'est pas encore là, dit franchement :

- l'**écran de mot de passe maître** : le niveau de chiffrement le plus fort est
  implémenté et testé, mais rien ne permet encore de l'activer depuis l'app —
  celle-ci tourne donc au niveau lié à l'appareil ;
- les identifiants **S3, WebDAV et FTP** sont encore dans la base locale en
  clair : seuls les jetons Jellyfin et les addons sont passés au coffre ;

- le **mode TV** (navigation à la télécommande) ;
- le **téléchargement hors-ligne** ;
- la **sélection de pistes** sur Android ;
- le **proxy de flux local sur Android** — donc, sur mobile, le FTP et les
  dossiers HLS d'un bucket ne sont pas encore lisibles (les fichiers simples, si) ;
- les paquets **Windows et macOS** ne sont pas publiés sur le serveur de mises à
  jour : ils se construisent sur leur propre système (`jpackage` ne compile pas
  pour une autre plateforme).

---

## ⚖️ Avertissement légal

Lumen Streaming est un **client logiciel**, comme un navigateur web.

- Lumen **ne fournit aucun contenu**. Aucun fichier vidéo, audio, ou sous-titre n'est hébergé ou distribué par le projet.
- Lumen **ne gère aucun serveur**. Toutes les sources sont connectées **directement** par l'utilisateur.
- Les **QR Codes** sont des liens locaux. Ils ne sont jamais envoyés, stockés ou traités par des serveurs appartenant à Lumen.
- L'utilisateur est **seul responsable** des sources qu'il ajoute et des QR Codes qu'il partage.

**Lumen est un outil. Son usage relève de la responsabilité de l'utilisateur.**

## Licence

Projet personnel, publié tel quel.
