import type { Metadata } from "next";
import Image from "next/image";
import PageHeader from "@/components/PageHeader";

export const metadata: Metadata = { title: "Captures d'écran" };

// `ready: true` une fois le fichier déposé dans public/screenshots/ — pas de
// détection automatique : un test en CI a montré qu'un accès fs.existsSync()
// au moment du build ne se comporte pas de façon fiable sous Turbopack.
const SLOTS: { file: string; caption: string; ready: boolean }[] = [
  { file: "accueil.png", caption: "Accueil — carrousel et rangées éditoriales", ready: false },
  { file: "fiche-film.png", caption: "Fiche film, avec métadonnées TMDB", ready: false },
  { file: "lecteur.png", caption: "Lecteur — qualité, pistes, statistiques de flux", ready: false },
  { file: "service-bucket.png", caption: "Onglet Service — assistant de connexion à un bucket", ready: false },
  { file: "profils.png", caption: "Profils du foyer et avatars", ready: false },
  { file: "parametres.png", caption: "Paramètres, synchronisés avec le compte serveur", ready: false },
];

export default function Captures() {
  return (
    <div className="mx-auto max-w-5xl px-6 py-16 sm:py-20">
      <PageHeader
        title="Captures d'écran"
        lede="L'interface plutôt que sa description."
      />
      <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        {SLOTS.map((s) => {
          return (
            <figure
              key={s.file}
              className="overflow-hidden rounded-xl border border-lumen-surface-high bg-lumen-surface"
            >
              {s.ready ? (
                <Image
                  src={`/lumen-streaming/screenshots/${s.file}`}
                  alt={s.caption}
                  width={640}
                  height={360}
                  className="aspect-video w-full object-cover"
                />
              ) : (
                <div className="flex aspect-video w-full items-center justify-center border-b border-dashed border-lumen-surface-high text-xs text-lumen-muted">
                  Capture à venir
                </div>
              )}
              <figcaption className="px-4 py-3 text-sm text-lumen-muted">{s.caption}</figcaption>
            </figure>
          );
        })}
      </div>
    </div>
  );
}
