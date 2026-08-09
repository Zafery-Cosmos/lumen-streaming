import type { Metadata } from "next";
import "./globals.css";
import Nav from "@/components/Nav";
import Footer from "@/components/Footer";

export const metadata: Metadata = {
  title: {
    default: "Lumen Streaming",
    template: "%s · Lumen Streaming",
  },
  description:
    "Un client Jellyfin natif (PC et Android) qui ressemble enfin à un service de streaming : accueil éditorial, sources multiples, lecteur maison, identifiants chiffrés.",
  icons: { icon: "/lumen-streaming/logo.png" },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="fr" className="h-full">
      <body className="flex min-h-full flex-col bg-lumen-bg text-lumen-fg antialiased">
        <Nav />
        <main className="flex-1">{children}</main>
        <Footer />
      </body>
    </html>
  );
}
