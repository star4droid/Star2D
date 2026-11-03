#!/usr/bin/env bash
# CI helper: builds an xdelta3 patch from the APK of the latest GitHub release to the APK just built.
#   tools/ci_make_patch.sh <new.apk> <out-dir> <version-name>
# Writes <out-dir>/Star2D-<version>-<oldmd5:8>-to-<newmd5:8>.xdelta (+ .txt with both MD5s) and sets
# PATCH_FILE / PATCH_INFO / PATCH_BASE_TAG in $GITHUB_ENV. Does nothing (exit 0) when there is no earlier release APK.
# Needs: gh (GH_TOKEN), xdelta3. The patch is verified: decoding it with the old APK must give the new APK byte for byte.
set -uo pipefail
NEW="$1"; OUT="$2"; VER="$3"
mkdir -p "$OUT"
WORK="$(mktemp -d)"
TAG="$(gh release list --limit 1 --json tagName --jq '.[0].tagName' 2>/dev/null || true)"
if [ -z "$TAG" ]; then echo "No earlier release: no patch."; exit 0; fi
if ! gh release download "$TAG" --pattern '*.apk' --dir "$WORK" 2>/dev/null; then echo "Release $TAG has no APK: no patch."; exit 0; fi
OLD="$(ls "$WORK"/*.apk | head -1)"
if ! command -v xdelta3 >/dev/null; then sudo apt-get update -qq && sudo apt-get install -y -qq xdelta3; fi
OLD_MD5="$(md5sum "$OLD" | cut -d' ' -f1)"; NEW_MD5="$(md5sum "$NEW" | cut -d' ' -f1)"
if [ "$OLD_MD5" = "$NEW_MD5" ]; then echo "Same APK as the release: no patch."; exit 0; fi
P="$OUT/Star2D-$VER-${OLD_MD5:0:8}-to-${NEW_MD5:0:8}.xdelta"
xdelta3 -e -9 -f -s "$OLD" "$NEW" "$P"
xdelta3 -d -f -s "$OLD" "$P" "$WORK/check.apk"
if [ "$(md5sum "$WORK/check.apk" | cut -d' ' -f1)" != "$NEW_MD5" ]; then echo "Patch check FAILED"; rm -f "$P"; exit 1; fi
{
  echo "Patch from release $TAG"
  echo "old apk md5: $OLD_MD5"
  echo "new apk md5: $NEW_MD5"
  echo "apply: xdelta3 -d -s old.apk $(basename "$P") new.apk"
} > "$P.txt"
echo "Patch: $P ($(du -h "$P" | cut -f1))"
{ echo "PATCH_FILE=$P"; echo "PATCH_INFO=$P.txt"; echo "PATCH_BASE_TAG=$TAG"; } >> "${GITHUB_ENV:-/dev/null}"
