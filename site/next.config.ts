import type { NextConfig } from "next";

// Site publié sur GitHub Pages en tant que « projet », donc servi depuis
// zafery-cosmos.github.io/lumen-streaming/ — sans basePath, tous les assets
// pointeraient vers la racine du domaine et resteraient introuvables.
const REPO = "lumen-streaming";

const nextConfig: NextConfig = {
  output: "export",
  basePath: `/${REPO}`,
  assetPrefix: `/${REPO}/`,
  images: { unoptimized: true },
  // GitHub Pages n'a pas de réécriture d'URL propre : sans ceci, l'export ne
  // produit que fonctionnalites.html, et une adresse partagée avec un / final
  // (le réflexe le plus courant) rend un 404 pur.
  trailingSlash: true,
};

export default nextConfig;
