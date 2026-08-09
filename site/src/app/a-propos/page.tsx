import type { Metadata } from "next";
import PageHeader from "@/components/PageHeader";

export const metadata: Metadata = { title: "À propos" };

export default function APropos() {
  return (
    <div className="mx-auto max-w-3xl px-6 py-16 sm:py-20">
      <PageHeader title="À propos" lede="D'où vient ce logiciel, et sous quelles conditions vous pouvez vous en servir." />

      <section className="rounded-xl border border-lumen-accent/30 bg-lumen-surface p-6">
        <h2 className="text-lg font-semibold text-lumen-fg">Ce logiciel est codé avec une IA</h2>
        <div className="mt-4 space-y-3 text-sm text-lumen-muted">
          <p>
            Le code de Lumen est écrit avec l&apos;assistance d&apos;une intelligence artificielle, sous supervision
            humaine à chaque étape : je spécifie ce qui doit être fait, je teste en conditions réelles sur mes
            propres appareils — desktop et Android — et je décide ce qui est publié.
          </p>
          <p>
            L&apos;IA écrit une grande partie du code et de la documentation ; les choix d&apos;architecture, les
            arbitrages de sécurité et la responsabilité du résultat final restent les miens. Rien n&apos;est publié
            sans être passé par un usage réel — y compris les correctifs de sécurité, testés avant d&apos;être
            déployés.
          </p>
          <p>
            Le code est intégralement open source, sous une licence qui interdit de le fermer. Vous n&apos;avez pas
            à me croire sur parole : vous pouvez le lire.
          </p>
        </div>
      </section>

      <section className="mt-14">
        <h2 className="text-xl font-semibold text-lumen-fg">⚖️ Avertissement légal</h2>
        <div className="mt-4 space-y-2 text-sm text-lumen-muted">
          <p>Lumen Streaming est un client logiciel, comme un navigateur web.</p>
          <ul className="list-disc space-y-2 pl-5">
            <li>Lumen ne fournit aucun contenu. Aucun fichier vidéo, audio, ou sous-titre n&apos;est hébergé ou distribué par le projet.</li>
            <li>Lumen ne gère aucun serveur. Toutes les sources sont connectées directement par l&apos;utilisateur.</li>
            <li>Les QR Codes sont des liens locaux. Ils ne sont jamais envoyés, stockés ou traités par des serveurs appartenant à Lumen.</li>
            <li>L&apos;utilisateur est seul responsable des sources qu&apos;il ajoute et des QR Codes qu&apos;il partage.</li>
          </ul>
          <p className="pt-2 font-medium text-lumen-fg">Lumen est un outil. Son usage relève de la responsabilité de l&apos;utilisateur.</p>
        </div>
      </section>

      <section className="mt-14">
        <h2 className="text-xl font-semibold text-lumen-fg">Licence</h2>
        <div className="mt-4 space-y-3 text-sm text-lumen-muted">
          <p>
            <strong className="text-lumen-fg">GNU General Public License v3.0</strong> — texte complet dans{" "}
            <a
              href="https://github.com/Zafery-Cosmos/lumen-streaming/blob/main/LICENSE"
              target="_blank"
              rel="noreferrer"
              className="text-lumen-fg underline decoration-lumen-surface-high underline-offset-4 hover:decoration-lumen-accent"
            >
              LICENSE
            </a>
            . Copyright © 2026 Zafery-Cosmos.
          </p>
          <p>Ce que vous pouvez faire, librement et sans rien demander :</p>
          <ul className="list-disc space-y-1 pl-5">
            <li>télécharger, lire, utiliser le code ;</li>
            <li>le modifier : changer l&apos;interface, ajouter des fonctions, enlever ce qui ne sert pas ;</li>
            <li>publier et redistribuer vos versions, y compris publiquement.</li>
          </ul>
          <p>
            La contrepartie, et c&apos;est la seule : si vous distribuez une version modifiée, vous devez publier son
            code source sous cette même licence. Personne ne peut donc reprendre Lumen pour en faire un produit
            fermé.
          </p>
          <p>
            <strong className="text-lumen-fg">Pourquoi la GPL plutôt qu&apos;une licence « pas de revente » ?</strong>{" "}
            Le lecteur du bureau s&apos;appuie sur vlcj, lui-même publié sous GPLv3 : distribuer Lumen sous une
            licence qui interdirait l&apos;usage commercial serait juridiquement incompatible avec cette dépendance.
            La GPL n&apos;interdit pas de vendre, elle interdit de fermer le code. Elle est en prime reconnue par
            F-Droid, contrairement aux licences non commerciales.
          </p>
        </div>
      </section>
    </div>
  );
}
