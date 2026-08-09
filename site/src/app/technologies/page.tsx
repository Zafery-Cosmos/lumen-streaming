import type { Metadata } from "next";
import PageHeader from "@/components/PageHeader";

export const metadata: Metadata = { title: "Technologies" };

const STACK: { name: string; role: string }[] = [
  { name: "Kotlin Compose Multiplatform", role: "Framework de base : une seule base de code Kotlin pour le bureau et Android, interface Compose partagée." },
  { name: "Gradle 9 · Kotlin 2.4", role: "Chaîne de compilation, avec la cible KMP dédiée d'Android Gradle Plugin 9." },
  { name: "Ktor", role: "Client HTTP pour les API Jellyfin, TMDB et Stremio — écrites à la main plutôt qu'avec un SDK généraliste." },
  { name: "SQLDelight", role: "Base de données locale : profils, PIN hachés, reprise de lecture par profil, sources configurées." },
  { name: "vlcj + libVLC", role: "Décodage vidéo sur le bureau, en rendu par callback pour que les contrôles Compose restent visibles au-dessus." },
  { name: "Media3 / ExoPlayer", role: "Lecture vidéo sur Android." },
  { name: "Coil 3", role: "Chargement et cache des images (affiches, avatars, logos)." },
  { name: "ZXing", role: "Génération des QR codes d'export/import de sources." },
  { name: "commons-net", role: "Client FTP." },
  { name: "JSch (fork mwiede)", role: "Client SFTP — choisi à la place de sshj, dont la dépendance BouncyCastle livre des archives signées qui rendaient le paquet final illisible par la JVM." },
  { name: "AES-256-GCM (JCA)", role: "Chiffrement du conteneur d'identifiants .lmn, sans bibliothèque tierce." },
  { name: "Android Keystore / StrongBox", role: "Détention de la clé de chiffrement côté appareil, jamais exposée au code applicatif." },
];

const CHOICES: { title: string; why: string }[] = [
  {
    title: "Client API écrit à la main plutôt qu'un SDK officiel",
    why: "Le SDK Jellyfin officiel expose des centaines d'endpoints pour une fraction réellement utilisée, et n'est pas pleinement multiplateforme.",
  },
  {
    title: "libVLC en rendu par callback",
    why: "Sur Linux, une surface vidéo système passe au-dessus de Compose : les contrôles seraient invisibles pendant la lecture. libVLC décode en mémoire, Compose dessine par-dessus.",
  },
  {
    title: "Interface de lecteur 100 % maison",
    why: "Aucun contrôle standard du système : la même interface sur toutes les plateformes, derrière une interface PlayerEngine commune à toutes les cibles.",
  },
  {
    title: "Signature S3 (SigV4) écrite à la main",
    why: "Une centaine de lignes remplacent un SDK de plusieurs mégaoctets, et couvrent S3, R2, B2 et tous les fournisseurs compatibles d'un seul coup.",
  },
  {
    title: "Proxy de flux local",
    why: "Ni le FTP, ni un dossier HLS hébergé dans un bucket privé ne se donnent à un lecteur vidéo tels quels. Le proxy les réexpose en HTTP propre, en signant les segments à la demande.",
  },
  {
    title: "Serveur de mises à jour en Python pur, sans dépendance",
    why: "Un seul fichier, aucun framework — juste assez pour diffuser un événement SSE et servir un fichier avec reprise par plage (Range).",
  },
];

export default function Technologies() {
  return (
    <div className="mx-auto max-w-5xl px-6 py-16 sm:py-20">
      <PageHeader
        title="Technologies"
        lede="Kotlin Compose Multiplatform pour l'application ; le site que vous lisez est en Next.js, exporté en statique et publié sur GitHub Pages."
      />

      <section>
        <h2 className="text-xl font-semibold text-lumen-fg">Architecture</h2>
        <pre className="mt-6 overflow-x-auto rounded-lg border border-lumen-surface-high bg-lumen-surface p-5 text-xs leading-relaxed text-lumen-muted">
{`lumen/
├── shared/          ~85 % du code : UI, client API, domaine, base locale
│   ├── api/         clients Jellyfin, TMDB et Stremio (écrits à la main)
│   ├── domain/      profils, réglages, clients S3/WebDAV/FTP, indexeur de bucket
│   ├── player/      PlayerEngine commun + moteur torrent + proxy de flux
│   ├── security/    coffre chiffré .lmn, clés système, contrôles d'exécution
│   ├── update/      mises à jour en direct (SSE), PC et Android
│   └── ui/          écrans Compose et design system
├── androidApp/      Media3 / ExoPlayer
└── desktopApp/      libVLC en rendu par callback`}
        </pre>
      </section>

      <section className="mt-16">
        <h2 className="text-xl font-semibold text-lumen-fg">Bibliothèque par bibliothèque</h2>
        <div className="mt-6 overflow-hidden rounded-xl border border-lumen-surface-high">
          <table className="w-full border-collapse text-sm">
            <tbody>
              {STACK.map((s, i) => (
                <tr key={s.name} className={i % 2 === 0 ? "bg-lumen-bg" : "bg-lumen-surface/40"}>
                  <td className="whitespace-nowrap px-4 py-3 font-medium text-lumen-fg align-top">{s.name}</td>
                  <td className="px-4 py-3 text-lumen-muted">{s.role}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="mt-16">
        <h2 className="text-xl font-semibold text-lumen-fg">Choix d&apos;architecture, et pourquoi</h2>
        <div className="mt-6 space-y-4">
          {CHOICES.map((c) => (
            <div key={c.title} className="rounded-lg border border-lumen-surface-high bg-lumen-surface p-5">
              <h3 className="font-medium text-lumen-fg">{c.title}</h3>
              <p className="mt-1.5 text-sm text-lumen-muted">{c.why}</p>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
