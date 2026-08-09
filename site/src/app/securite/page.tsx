import type { Metadata } from "next";
import PageHeader from "@/components/PageHeader";

export const metadata: Metadata = { title: "Sécurité" };

export default function Securite() {
  return (
    <div className="mx-auto max-w-3xl px-6 py-16 sm:py-20">
      <PageHeader
        title="Sécurité"
        lede="Brancher un serveur Jellyfin, un bucket ou un partage WebDAV, c'est confier à l'app des jetons et des clés qui ouvrent une médiathèque personnelle. Voici comment ils sont protégés."
      />

      <div className="space-y-12 text-lumen-fg">
        <section>
          <h2 className="text-xl font-semibold">Le conteneur .lmn</h2>
          <ul className="mt-4 space-y-3 text-sm text-lumen-muted">
            <li>
              <strong className="text-lumen-fg">AES-256-GCM</strong>, avec un nonce unique à chaque écriture et un
              en-tête authentifié : modifier le fichier, ne serait-ce que d&apos;un octet, ou tenter d&apos;y
              rétrograder le niveau de chiffrement, rend le contenu illisible plutôt que d&apos;ouvrir une brèche.
            </li>
            <li>
              <strong className="text-lumen-fg">Clé détenue par le système</strong> : Android Keystore, avec
              StrongBox lorsque l&apos;appareil embarque une puce dédiée. La clé n&apos;en sort jamais — copier les
              fichiers de l&apos;app sur un autre téléphone ne donne rien.
            </li>
            <li>
              <strong className="text-lumen-fg">Un second niveau, à mot de passe maître</strong> : la clé est alors
              dérivée à chaque ouverture (PBKDF2-HMAC-SHA256, 210 000 tours) et vit uniquement en mémoire — rien sur
              le disque ne permet plus de déchiffrer. Le moteur est en place et testé ; l&apos;écran de saisie pour
              l&apos;activer depuis l&apos;app n&apos;est pas encore livré.
            </li>
          </ul>
        </section>

        <section>
          <h2 className="text-xl font-semibold">Défense en profondeur</h2>
          <ul className="mt-4 space-y-3 text-sm text-lumen-muted">
            <li>Essais d&apos;ouverture freinés progressivement (jusqu&apos;à 5 minutes d&apos;attente), et le compteur survit au redémarrage.</li>
            <li>Comparaisons à durée constante — pas de raccourci qui laisserait deviner un mot de passe caractère par caractère via le temps de réponse.</li>
            <li>Clés effacées de la mémoire au verrouillage.</li>
            <li>
              L&apos;appareil est analysé (root, débogueur attaché, émulateur, signature du paquet altérée) et
              l&apos;utilisateur en est informé — <strong className="text-lumen-fg">sans blocage</strong> : un
              blocage se contourne, et pénaliserait surtout ceux qui maîtrisent leur propre matériel.
            </li>
          </ul>
        </section>

        <section>
          <h2 className="text-xl font-semibold">Ce qui est couvert</h2>
          <p className="mt-4 text-sm text-lumen-muted">
            Tous les identifiants de connexion : jetons Jellyfin, adresses et clés d&apos;API des addons, clés
            d&apos;accès des buckets, mots de passe WebDAV et FTP. La base locale ne conserve que des{" "}
            <strong className="text-lumen-fg">références</strong> vers le coffre — copiée ou exfiltrée, elle ne
            contient aucun secret exploitable. La migration depuis les versions précédentes est automatique et
            silencieuse : les valeurs en clair sont reprises, chiffrées, puis effacées de leur ancien emplacement.
          </p>
        </section>

        <section>
          <h2 className="text-xl font-semibold">Deux choix assumés, plutôt que du théâtre de sécurité</h2>
          <div className="mt-4 space-y-4 text-sm text-lumen-muted">
            <p>
              <strong className="text-lumen-fg">Pas d&apos;épinglage de certificat.</strong> La connexion se fait à{" "}
              <em>votre</em> serveur, souvent en adresse locale avec un certificat auto-signé : épingler casserait
              la fonction principale de l&apos;app pour un gain nul dans ce scénario.
            </p>
            <p>
              <strong className="text-lumen-fg">Pas d&apos;algorithme caché.</strong> La solidité vient de la clé,
              jamais du secret de la méthode — un chiffrement qu&apos;il faut dissimuler pour tenir ne tient pas, et
              sur un projet open source il ne le resterait pas dix minutes. Le code est lisible ; c&apos;est ce qui
              permet de le vérifier soi-même plutôt que de devoir y croire sur parole.
            </p>
          </div>
        </section>

        <section>
          <h2 className="text-xl font-semibold">Ce que ça ne fait pas</h2>
          <p className="mt-4 text-sm text-lumen-muted">
            L&apos;export d&apos;une source en QR code n&apos;est pas un chiffrement : GZip et Base64 transportent
            la donnée, ils ne la protègent pas. Qui décode le code lit la clé d&apos;accès en clair, exactement
            comme un QR code de mot de passe Wi-Fi. La sécurité vient de à qui vous le montrez.
          </p>
        </section>
      </div>
    </div>
  );
}
