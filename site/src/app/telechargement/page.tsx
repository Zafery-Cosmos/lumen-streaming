import type { Metadata } from "next";
import PageHeader from "@/components/PageHeader";

export const metadata: Metadata = { title: "Télécharger" };

const REPO = "https://github.com/Zafery-Cosmos/lumen-streaming";

const PLATFORMS: { name: string; status: "ready" | "build"; note: string }[] = [
  { name: "Linux (.deb / .rpm)", status: "ready", note: "Publié sur les releases GitHub." },
  { name: "Android (.apk)", status: "ready", note: "Publié sur les releases GitHub. Installation manuelle (hors Play Store)." },
  { name: "Windows (.msi)", status: "build", note: "jpackage ne compile pas pour une autre plateforme que la sienne : à construire depuis Windows." },
  { name: "macOS (.dmg)", status: "build", note: "Même contrainte : à construire depuis macOS." },
];

export default function Telechargement() {
  return (
    <div className="mx-auto max-w-3xl px-6 py-16 sm:py-20">
      <PageHeader
        title="Télécharger"
        lede="Un serveur Jellyfin 10.10+ est nécessaire — Lumen ne fonctionne pas sans."
      />

      <a
        href={`${REPO}/releases/latest`}
        target="_blank"
        rel="noreferrer"
        className="inline-block rounded-md bg-lumen-accent px-5 py-2.5 font-semibold text-white transition-opacity hover:opacity-90"
      >
        Voir la dernière version sur GitHub
      </a>

      <div className="mt-10 overflow-hidden rounded-xl border border-lumen-surface-high">
        <table className="w-full border-collapse text-sm">
          <tbody>
            {PLATFORMS.map((p, i) => (
              <tr key={p.name} className={i % 2 === 0 ? "bg-lumen-bg" : "bg-lumen-surface/40"}>
                <td className="whitespace-nowrap px-4 py-3 font-medium text-lumen-fg align-top">{p.name}</td>
                <td className="px-4 py-3 text-lumen-muted align-top">
                  <span
                    className={
                      p.status === "ready"
                        ? "mr-2 inline-block rounded px-1.5 py-0.5 text-xs font-medium text-lumen-accent"
                        : "mr-2 inline-block rounded px-1.5 py-0.5 text-xs font-medium text-lumen-muted"
                    }
                  >
                    {p.status === "ready" ? "Publié" : "À compiler"}
                  </span>
                  {p.note}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <section className="mt-14">
        <h2 className="text-xl font-semibold text-lumen-fg">Compiler soi-même</h2>
        <p className="mt-3 text-sm text-lumen-muted">
          Nécessite un JDK 21, et le SDK Android uniquement pour produire l&apos;APK.
        </p>
        <pre className="mt-4 overflow-x-auto rounded-lg border border-lumen-surface-high bg-lumen-surface p-5 text-xs leading-relaxed text-lumen-muted">
{`git clone ${REPO}.git
cd lumen-streaming

# Clé TMDB (métadonnées, logos, réorganisation des séries)
echo "tmdb.api.key=VOTRE_CLE" >> local.properties
echo "sdk.dir=/chemin/vers/Android/Sdk" >> local.properties   # Android seulement

./gradlew :desktopApp:packageUberJarForCurrentOS   # → desktopApp/build/compose/jars/
./gradlew :androidApp:assembleDebug                # → androidApp/build/outputs/apk/`}
        </pre>
      </section>

      <section className="mt-14">
        <h2 className="text-xl font-semibold text-lumen-fg">Premier lancement</h2>
        <p className="mt-3 text-sm text-lumen-muted">
          Adresse du serveur (la saisie est tolérante — <code className="text-lumen-fg">192.168.1.10</code> suffit),
          puis connexion classique ou Quick Connect, puis création du profil. Les mises à jour suivantes sont
          ensuite proposées automatiquement, sans repasser par ici.
        </p>
      </section>
    </div>
  );
}
