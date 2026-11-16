#!/usr/bin/env bash
# Compatibility probe, run inside an Android emulator by .github/workflows/emulator.yml.
#
# It installs a DEBUG build, opens the official site in probe mode and records what the engine
# reports: engine version, HTTP status, how much of the page rendered, JavaScript errors.
# It does not sign in and does not try to get past any bot check: a blocked run is reported
# as blocked. Results go to out/ and to the job summary.
#
#   probe.sh <apk> <engine: webview|gecko> <label>
set -u
APK=$1; ENGINE=$2; LABEL=$3
URL=${PROBE_URL:-https://claude.ai/login}
PKG=io.github.forkyfor.oldphone
mkdir -p out

API=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
REL=$(adb shell getprop ro.build.version.release | tr -d '\r')
WV=$(adb shell dumpsys package com.google.android.webview 2>/dev/null | grep -m1 versionName | tr -d ' \r')
[ -z "$WV" ] && WV=$(adb shell dumpsys package com.android.webview 2>/dev/null | grep -m1 versionName | tr -d ' \r')

adb uninstall "$PKG" >/dev/null 2>&1
if [ "$API" -ge 23 ]; then adb install -g "$APK"; else adb install "$APK"; fi
INSTALL=$?

# 1) Does the app start, and does the first-run guide work when really tapped through?
adb logcat -c
if [ "${UI_WALK:-false}" = "true" ]; then
  python3 "$(dirname "$0")/ui-walk.py" > "out/${LABEL}-ui-walk.txt" 2>&1
  case $? in 0) WALK=ok ;; 2) WALK=unavailable ;; *) WALK=FAILED ;; esac
  cat "out/${LABEL}-ui-walk.txt"
else
  adb shell am start -W -n "$PKG/.MainActivity" >/dev/null
  sleep 8
  WALK=skipped
fi
adb exec-out screencap -p > "out/${LABEL}-setup.png"
if adb shell ps | grep -q "$PKG"; then START=ok; else START=crashed; fi
adb logcat -d > "out/${LABEL}-logcat-ui.txt"
UICRASH=$(grep -c "FATAL EXCEPTION" "out/${LABEL}-logcat-ui.txt")
adb shell am force-stop "$PKG"

# 2) Does the official site render in this engine?
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity" --ez probe true --es probe_engine "$ENGINE" --es probe_url "$URL" >/dev/null
sleep 100
adb exec-out screencap -p > "out/${LABEL}-site.png"
adb logcat -d > "out/${LABEL}-logcat.txt"
grep "OPC_PROBE" "out/${LABEL}-logcat.txt" | sed 's/.*OPC_PROBE: //' > "out/${LABEL}-probe.jsonl"
CRASH=$(grep -c "FATAL EXCEPTION" "out/${LABEL}-logcat.txt")

echo "=== PROBE RESULT ${LABEL} ==="
CRASH=$((CRASH + UICRASH))
{
  echo "android=${REL} api=${API} stock_webview=${WV:-unknown} engine=${ENGINE} install=${INSTALL} start=${START} ui_walk=${WALK} fatal_exceptions=${CRASH} url=${URL}"
  cat "out/${LABEL}-probe.jsonl"
} > "out/${LABEL}-result.txt"
cat "out/${LABEL}-result.txt"
echo "=== END ${LABEL} ==="

{
  echo "### ${LABEL}"
  echo "Android ${REL} (API ${API}) · stock WebView \`${WV:-unknown}\` · engine \`${ENGINE}\` · install=${INSTALL} · app start=${START} · guide walk=${WALK} · fatal exceptions=${CRASH}"
  echo '```json'
  cat "out/${LABEL}-probe.jsonl"
  echo '```'
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}"

[ "$INSTALL" = 0 ] && [ "$START" = ok ] && [ "$CRASH" = 0 ] && [ "$WALK" != FAILED ]
