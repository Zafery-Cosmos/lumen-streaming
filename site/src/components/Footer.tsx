import Link from "next/link";

export default function Footer() {
  return (
    <footer className="mt-auto border-t border-lumen-surface-high">
      <div className="mx-auto max-w-5xl px-6 py-8 text-sm text-lumen-muted">
        <p>
          Ce logiciel est codé avec l&apos;assistance d&apos;une intelligence artificielle,
          sous supervision humaine à chaque étape.{" "}
          <Link href="/a-propos" className="text-lumen-fg underline decoration-lumen-surface-high underline-offset-4 hover:decoration-lumen-accent">
            En savoir plus
          </Link>
          .
        </p>
        <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-1">
          <span>GPLv3</span>
          <span aria-hidden>·</span>
          <a href="https://github.com/Zafery-Cosmos/lumen-streaming" target="_blank" rel="noreferrer" className="hover:text-lumen-fg">
            Code source
          </a>
          <span aria-hidden>·</span>
          <Link href="/a-propos" className="hover:text-lumen-fg">
            Avertissement légal
          </Link>
        </div>
      </div>
    </footer>
  );
}
