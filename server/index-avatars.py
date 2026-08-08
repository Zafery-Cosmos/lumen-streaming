#!/usr/bin/env python3
"""
Indexe la banque d'avatars pour Lumen.

L'arborescence d'origine est déjà triée : movix/<plateforme>/<oeuvre>/*.png.
On en tire un index JSON groupé par ŒUVRE (film ou série), que l'app affiche
en onglets — plutôt qu'un mur de 1757 images sans repère.
"""
import json
import os

ROOT = "/opt/lumen-avatars"
OUT = os.path.join(ROOT, "index.json")

# Noms lisibles des plateformes (le dossier est en minuscules techniques).
PLATFORMS = {
    "crunchyroll": "Crunchyroll",
    "disney": "Disney",
    "disney+": "Disney+",
    "disney_channel": "Disney Channel",
    "hbo": "HBO",
    "marvel": "Marvel",
    "mickey": "Mickey",
    "netflix": "Netflix",
    "pixar": "Pixar",
    "prime_video": "Prime Video",
    "simpsons": "Les Simpson",
    "starwars": "Star Wars",
}


def pretty(name: str) -> str:
    """« black_butler » → « Black Butler », « aot » reste tel quel en majuscules."""
    cleaned = name.replace("_", " ").replace("-", " ").strip()
    if len(cleaned) <= 4 and cleaned.isalpha():
        return cleaned.upper()
    return " ".join(w[:1].upper() + w[1:] for w in cleaned.split())


def main():
    groups = []

    # Les avatars « neutres » de la racine : un groupe à part.
    generic = sorted(f for f in os.listdir(ROOT) if f.endswith(".png"))
    if generic:
        groups.append({
            "id": "generic",
            "collection": "Général",
            "work": "Avatars neutres",
            "avatars": [{"file": f, "url": f"/avatars/{f}"} for f in generic],
        })

    movix = os.path.join(ROOT, "movix")
    for platform in sorted(os.listdir(movix)) if os.path.isdir(movix) else []:
        pdir = os.path.join(movix, platform)
        if not os.path.isdir(pdir):
            continue
        collection = PLATFORMS.get(platform, pretty(platform))

        # Images posées directement dans la plateforme (pas de sous-œuvre).
        loose = sorted(f for f in os.listdir(pdir) if f.endswith(".png"))
        if loose:
            groups.append({
                "id": f"{platform}",
                "collection": collection,
                "work": collection,
                "avatars": [
                    {"file": f, "url": f"/avatars/movix/{platform}/{f}"} for f in loose
                ],
            })

        # Les œuvres : un onglet par film / série.
        for work in sorted(os.listdir(pdir)):
            wdir = os.path.join(pdir, work)
            if not os.path.isdir(wdir):
                continue
            files = sorted(f for f in os.listdir(wdir) if f.endswith(".png"))
            if not files:
                continue
            groups.append({
                "id": f"{platform}/{work}",
                "collection": collection,
                "work": pretty(work),
                "avatars": [
                    {"file": f, "url": f"/avatars/movix/{platform}/{work}/{f}"}
                    for f in files
                ],
            })

    index = {
        "count": sum(len(g["avatars"]) for g in groups),
        "groups": groups,
    }
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=1)

    print(f"{index['count']} avatars dans {len(groups)} groupes → {OUT}")


if __name__ == "__main__":
    main()
