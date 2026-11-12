#!/usr/bin/env bash
# CI helper: builds an xdelta3 patch from the APK of the PREVIOUS GitHub release (not the one being released) to the APK just built.
# With PATCH_BASE_APK=<old.apk> in the environment that APK is the base instead (no release needed).
#   tools/ci_make_patch.sh <new.apk> <out-dir> <version-name> [current-tag]
# Writes <out-dir>/Star2D-<previous-version>-to-<version>.xdelta (+ .txt with both MD5s) and sets
# PATCH_FILE / PATCH_INFO / PATCH_BASE_TAG in $GITHUB_ENV. Does nothing (exit 0) when there is no earlier release APK.
# Needs: gh (GH_TOKEN), xdelta3. The patch is verified: decoding it with the old APK must give the new APK byte for byte.
set -uo pipefail
NEW="$1"; OUT="$2"; VER="$3"; CUR="${4:-}"   # CUR = tag being released now: never used as the base
mkdir -p "$OUT"
WORK="$(mktemp -d)"
TAG=""; LABEL=""
if [ -n "${PATCH_BASE_APK:-}" ]; then
  # base given by the caller (an APK taken from an older run's artifact): label it with its version name + a short hash
  OLD="$PATCH_BASE_APK"
  if [ ! -f "$OLD" ]; then echo "PATCH_BASE_APK not found: $OLD"; exit 1; fi
  AAPT="$(ls "${ANDROID_HOME:-/nonexistent}"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)"
  BV=""
  [ -n "$AAPT" ] && BV="$("$AAPT" dump badging "$OLD" 2>/dev/null | sed -nE "s/.*versionName='([^']+)'.*/\\1/p" | head -1)"
  LABEL="${BV:-build}-$(md5sum "$OLD" | cut -c1-6)"
  TAG="artifact ($LABEL)"
else
  for T in $(gh release list --limit 10 --json tagName,isDraft,isPrerelease --jq '.[] | select(.isDraft|not) | .tagName' 2>/dev/null); do
    [ "$T" = "$CUR" ] && continue
    if gh release download "$T" --pattern 'Star2D-*.apk' --dir "$WORK" 2>/dev/null; then TAG="$T"; break; fi
  done
  if [ -z "$TAG" ]; then echo "No earlier release APK: no patch."; exit 0; fi
  OLD="$(ls "$WORK"/*.apk | head -1)"
  LABEL="${TAG#v}"
fi
if ! command -v xdelta3 >/dev/null; then sudo apt-get update -qq && sudo apt-get install -y -qq xdelta3; fi
OLD_MD5="$(md5sum "$OLD" | cut -d' ' -f1)"; NEW_MD5="$(md5sum "$NEW" | cut -d' ' -f1)"
if [ "$OLD_MD5" = "$NEW_MD5" ]; then echo "Same APK as the base: no patch."; exit 0; fi
P="$OUT/Star2D-$LABEL-to-$VER.xdelta"
xdelta3 -e -9 -f -s "$OLD" "$NEW" "$P"
xdelta3 -d -f -s "$OLD" "$P" "$WORK/check.apk"
if [ "$(md5sum "$WORK/check.apk" | cut -d' ' -f1)" != "$NEW_MD5" ]; then echo "Patch check FAILED"; rm -f "$P"; exit 1; fi
{
  echo "Patch from $TAG"
  echo "old apk md5: $OLD_MD5"
  echo "new apk md5: $NEW_MD5"
  echo "apply: xdelta3 -d -s old.apk $(basename "$P") new.apk"
} > "$P.txt"
echo "Patch: $P ($(du -h "$P" | cut -f1))"
{ echo "PATCH_FILE=$P"; echo "PATCH_INFO=$P.txt"; echo "PATCH_BASE_TAG=$TAG"; } >> "${GITHUB_ENV:-/dev/null}"
