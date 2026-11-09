#!/usr/bin/env bash
# Builds the Mesh2Motion "Use Your Model" page for the Star2D app ("M2M Motion" tool) into
# app/src/main/assets/m2m/ — code + small UI images only. Rigs/animations are NOT bundled: the app downloads them
# on demand from the SUDA3D server (GET /api/v1/m2m/manifest) and serves them to the page at /m2mdata/.
#
# Usage: tools/m2m/build_m2m.sh /path/to/mesh2motion-app   (tested with commit fd9fdeb2ba214d210e8e4f28015c2a3c06cd8f5b)
# Needs node 22 + npm. Mesh2Motion: code MIT © Scott Petrovic, assets CC0 (see app/src/main/assets/m2m/LICENSE-*.md).
set -euo pipefail
SRC="${1:?path to a mesh2motion-app checkout}"
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
OUT="$REPO/app/src/main/assets/m2m"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
( cd "$SRC" && git archive HEAD ) | tar -x -C "$WORK"
cd "$WORK"
patch -p1 < "$HERE/mesh2motion-star2d.patch"
mkdir -p static-app/images
# small UI images only (no asset library, animation previews, variations or test files)
cp -r static/images/icons static/images/*.svg static/images/favicon.png static/images/skeleton-joint-point.png static-app/images/
cp -r static/rig-reference static-app/
npm ci --no-audit --no-fund --ignore-scripts
npx vite build --config vite.app.config.js
rm -rf "$OUT"
mkdir -p "$OUT"
cp -r dist-app/. "$OUT/"
cp LICENSE-MIT.MD "$OUT/LICENSE-MIT.md"
cp LICENSE-CC0.MD "$OUT/LICENSE-CC0.md"
du -sh "$OUT"
