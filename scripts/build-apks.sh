#!/usr/bin/env bash
# Builds every APK this project publishes into dist/, with SHA-256 checksums.
#
#   SIGNING_KEYSTORE_FILE set  -> signed release APKs (small, optimised, updatable)
#   SIGNING_KEYSTORE_FILE unset -> debug APKs, clearly named "-debug"
#
# Used by the GitHub Actions workflows; works the same on any Linux/macOS/WSL shell.
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)
GECKO_ABIS=${GECKO_ABIS:-"arm64-v8a armeabi-v7a"}

if [ -n "${SIGNING_KEYSTORE_FILE:-}" ] && [ -f "${SIGNING_KEYSTORE_FILE}" ]; then
  TYPE=release; TASK=Release; SUFFIX=""
  echo "Signing key found: building SIGNED RELEASE APKs."
else
  TYPE=debug; TASK=Debug; SUFFIX="-debug"
  echo "No signing key: building DEBUG APKs (signed with a throw-away debug key)."
fi

rm -rf dist && mkdir -p dist
./gradlew --no-daemon ":app:assembleLite${TASK}"
cp "app/build/outputs/apk/lite/${TYPE}/app-lite-${TYPE}.apk" "dist/OldPhoneClaude-${VERSION}-lite${SUFFIX}.apk"

for ABI in ${GECKO_ABIS}; do
  ./gradlew --no-daemon ":app:assembleGecko${TASK}" "-Pabi=${ABI}"
  cp "app/build/outputs/apk/gecko/${TYPE}/app-gecko-${TYPE}.apk" "dist/OldPhoneClaude-${VERSION}-gecko-${ABI}${SUFFIX}.apk"
done

(cd dist && sha256sum ./*.apk | sed 's# \./# #' > SHA256SUMS.txt)
echo "${TYPE}" > dist/.build-type
echo
cat dist/SHA256SUMS.txt
ls -la dist
