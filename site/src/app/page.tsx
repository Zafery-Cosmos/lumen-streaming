import Link from "next/link";
import Image from "next/image";

const COMPARISON: [string, string, string][] = [
  ["Accueil", "Grilles de bibliothèques et de dossiers", "Carrousel plein écran + rangées éditoriales (Top 10, genres, Ma médiathèque)"],
  ["Contenu absent du serveur", "Impasse", "Addons Stremio, moteur torrent intégré"],
  ["Profils", "Comptes serveur uniquement", "Profils locaux du foyer : PIN, profil enfant, reprise par profil"],
  ["Séries mal rangées", "Affichées telles quelles", "Réorganisées via TMDB (numéros absolus → vraies saisons)"],
  ["Sources autres que Jellyfin", "Aucune", "Buckets S3/R2/B2, WebDAV, FTP — indexés et rangés sur l'accueil"],
  ["Identifiants stockés", "En clair côté client", "Chiffrés AES-256-GCM, clé tenue par le magasin de clés du système"],
  ["Mises à jour", "À la main", "En direct : publier prévient les apps ouvertes instantanément"],
  ["Lecteur", "Contrôles de base", "UI maison : vitesse, qualité, pistes, capture, statistiques de flux"],
];

export default function Home() {
  return (
    <div className="mx-auto max-w-5xl px-6 py-16 sm:py-24">
      <section className="flex flex-col items-start gap-6">
        <Image src="/lumen-streaming/logo.png" alt="Lumen" width={72} height={72} className="rounded-2xl" />
        <h1 className="text-4xl font-bold tracking-tight sm:text-5xl">
          Un client Jellyfin qui ressemble enfin
          <br className="hidden sm:block" /> à un service de streaming.
        </h1>
        <p className="max-w-2xl text-lg text-lumen-muted">
          Lumen est une application <strong className="text-lumen-fg">native</strong> (PC et Android) qui se
          branche sur votre serveur Jellyfin existant. Elle ne remplace rien côté serveur : elle utilise son API et
          son authentification, et remplace uniquement l&apos;interface — parce qu&apos;une médiathèque
          personnelle mérite mieux qu&apos;une grille de dossiers.
        </p>
        <div className="flex flex-wrap gap-3 pt-2">
          <Link
            href="/telechargement"
            className="rounded-md bg-lumen-accent px-5 py-2.5 font-semibold text-white transition-opacity hover:opacity-90"
          >
            Télécharger
          </Link>
          <Link
            href="/fonctionnalites"
            className="rounded-md border border-lumen-surface-high px-5 py-2.5 font-semibold transition-colors hover:border-lumen-accent hover:text-lumen-accent"
          >
            Voir les fonctionnalités
          </Link>
          <a
            href="https://github.com/Zafery-Cosmos/lumen-streaming"
            target="_blank"
            rel="noreferrer"
            className="rounded-md border border-lumen-surface-high px-5 py-2.5 font-semibold transition-colors hover:border-lumen-fg"
          >
            Code source (GPLv3)
          </a>
        </div>
      </section>

      <section className="mt-20">
        <h2 className="text-sm font-semibold uppercase tracking-wider text-lumen-muted">La différence</h2>
        <p className="mt-2 max-w-2xl text-lumen-muted">
          Les clients Jellyfin officiels sont d&apos;excellents explorateurs de médiathèque. Lumen est un{" "}
          <strong className="text-lumen-fg">service de streaming</strong> : lecture immédiate, et surtout la
          capacité de ne jamais être une impasse quand un titre n&apos;est pas sur le serveur.
        </p>

        <div className="mt-8 overflow-hidden rounded-xl border border-lumen-surface-high">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="bg-lumen-surface text-left text-lumen-muted">
                <th className="px-4 py-3 font-medium"> </th>
                <th className="px-4 py-3 font-medium">Client Jellyfin classique</th>
                <th className="px-4 py-3 font-medium text-lumen-accent">Lumen</th>
              </tr>
            </thead>
            <tbody>
              {COMPARISON.map(([label, before, after], i) => (
                <tr key={label} className={i % 2 === 0 ? "bg-lumen-bg" : "bg-lumen-surface/40"}>
                  <td className="px-4 py-3 font-medium text-lumen-fg">{label}</td>
                  <td className="px-4 py-3 text-lumen-muted">{before}</td>
                  <td className="px-4 py-3 text-lumen-fg">{after}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="mt-20 grid gap-6 sm:grid-cols-3">
        {[
          {
            title: "Toutes vos sources",
            body: "Jellyfin, dossiers HLS déjà transcodés, addons Stremio, buckets S3/R2/B2, WebDAV et FTP — une seule interface.",
          },
          {
            title: "Identifiants chiffrés",
            body: "AES-256-GCM, clé tenue par le magasin de clés du système. Copier les fichiers de l'app ailleurs ne donne rien.",
          },
          {
            title: "Open source, sans arrière-pensée",
            body: "GPLv3 : téléchargez, lisez, modifiez, redistribuez. Aucune fonction cachée, aucune télémétrie.",
          },
        ].map((c) => (
          <div key={c.title} className="rounded-xl border border-lumen-surface-high bg-lumen-surface p-6">
            <h3 className="font-semibold text-lumen-fg">{c.title}</h3>
            <p className="mt-2 text-sm text-lumen-muted">{c.body}</p>
          </div>
        ))}
      </section>
    </div>
  );
}
