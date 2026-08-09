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
};

export default nextConfig;
