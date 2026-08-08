#!/usr/bin/env bash
# Publie une version de Lumen : dépose les binaires, écrit le manifeste,
# et prévient INSTANTANÉMENT toutes les apps ouvertes (SSE).
#
#   ./publish.sh 0.2.0 "Correctif A|Correctif B" fichier1 [fichier2 ...]
#
# Les fichiers sont reconnus par leur nom : *.jar/AppImage → linux,
# *.msi/*.exe → windows, *.apk → android.
set -euo pipefail

ROOT=/opt/lumen-updates
FILES="$ROOT/files"
PORT="${LUMEN_UPDATE_PORT:-8500}"
TOKEN="${LUMEN_PUBLISH_TOKEN:-lumen-publish}"

VERSION="${1:?usage: publish.sh <version> <notes séparées par |> <fichiers...>}"
NOTES="${2:?}"
shift 2

mkdir -p "$FILES"

platforms_json=""
for f in "$@"; do
    [ -f "$f" ] || { echo "Fichier introuvable : $f" >&2; exit 1; }
    base="$(basename "$f")"
    # Le nom porte la version : les anciennes restent téléchargeables.
    target="$FILES/$base"
    cp -f "$f" "$target"

    size=$(stat -c %s "$target")
    sha=$(sha256sum "$target" | cut -d' ' -f1)

    case "$base" in
        *.msi|*.exe) plat=windows ;;
        *.apk)       plat=android ;;
        *.dmg)       plat=macos ;;
        *)           plat=linux ;;
    esac

    entry=$(printf '"%s":{"file":"%s","size":%s,"sha256":"%s"}' "$plat" "$base" "$size" "$sha")
    platforms_json="${platforms_json:+$platforms_json,}$entry"
    echo "  + $plat : $base ($(numfmt --to=iec "$size"))"
done

# Les notes « A|B|C » deviennent un tableau JSON.
notes_json=$(python3 - "$NOTES" <<'PY'
import json, sys
print(json.dumps([n.strip() for n in sys.argv[1].split("|") if n.strip()]))
PY
)

payload=$(printf '{"version":"%s","notes":%s,"platforms":{%s}}' "$VERSION" "$notes_json" "$platforms_json")

echo "$payload" | curl -sS -X POST "http://127.0.0.1:$PORT/api/publish" \
    -H "Content-Type: application/json" \
    -H "X-Publish-Token: $TOKEN" \
    --data-binary @- | python3 -m json.tool

echo "Version $VERSION publiée — les apps ouvertes sont prévenues."
