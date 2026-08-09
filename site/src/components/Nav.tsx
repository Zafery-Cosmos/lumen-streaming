import Link from "next/link";
import Image from "next/image";

const LINKS = [
  { href: "/fonctionnalites", label: "Fonctionnalités" },
  { href: "/technologies", label: "Technologies" },
  { href: "/securite", label: "Sécurité" },
  { href: "/captures", label: "Captures" },
  { href: "/telechargement", label: "Télécharger" },
  { href: "/a-propos", label: "À propos" },
];

export default function Nav() {
  return (
    <header className="border-b border-lumen-surface-high">
      <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-4 px-6 py-4">
        <Link href="/" className="flex items-center gap-3">
          <Image src="/lumen-streaming/logo.png" alt="Lumen" width={32} height={32} className="rounded-lg" />
          <span className="text-lg font-semibold tracking-tight">Lumen Streaming</span>
        </Link>
        <nav className="flex flex-wrap items-center gap-x-5 gap-y-2 text-sm text-lumen-muted">
          {LINKS.map((l) => (
            <Link key={l.href} href={l.href} className="transition-colors hover:text-lumen-fg">
              {l.label}
            </Link>
          ))}
          <a
            href="https://github.com/Zafery-Cosmos/lumen-streaming"
            target="_blank"
            rel="noreferrer"
            className="rounded-md border border-lumen-surface-high px-3 py-1.5 font-medium text-lumen-fg transition-colors hover:border-lumen-accent hover:text-lumen-accent"
          >
            GitHub
          </a>
        </nav>
      </div>
    </header>
  );
}
