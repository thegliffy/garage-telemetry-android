#!/usr/bin/env bash
#
# Capture OBD frames from the phone and decode them.
#
#   ./tools/capture.sh                    capture 20s, print decoded values
#   ./tools/capture.sh 60                 capture 60s
#   ./tools/capture.sh --odometer 12345   capture, then find the odometer field
#   ./tools/capture.sh --speed 65         capture, then find the speed field
#
# The app must be connected to the adapter and logging (car awake) while this runs.

set -euo pipefail

cd "$(dirname "$0")/.."

SECONDS_TO_CAPTURE=20
MODE=""
KNOWN_VALUE=""

while [ $# -gt 0 ]; do
    case "$1" in
        --odometer) MODE="odometer"; KNOWN_VALUE="${2:?--odometer needs the dash reading}"; shift 2 ;;
        --speed)    MODE="speed";    KNOWN_VALUE="${2:?--speed needs the indicated speed}"; shift 2 ;;
        -h|--help)  sed -n '2,12p' "$0"; exit 0 ;;
        *)          SECONDS_TO_CAPTURE="$1"; shift ;;
    esac
done

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
[ -x "$ADB" ] || ADB="$(command -v adb || true)"
if [ -z "$ADB" ] || [ ! -x "$ADB" ]; then
    echo "adb not found. Expected $ANDROID_HOME/platform-tools/adb" >&2
    exit 1
fi

if ! "$ADB" get-state >/dev/null 2>&1; then
    echo "No device. Plug the phone in over USB, unlock it, and accept the debugging" >&2
    echo "prompt if it appears. Check with: $ADB devices" >&2
    exit 1
fi

if ! "$ADB" shell pidof com.garagepi.telemetry >/dev/null 2>&1; then
    echo "Warning: the app does not appear to be running. Open it and connect to the" >&2
    echo "adapter first, otherwise this capture will be empty." >&2
fi

OUT="capture-$(date +%Y%m%d-%H%M%S).txt"

echo "Capturing ${SECONDS_TO_CAPTURE}s of adapter traffic..."
"$ADB" logcat -c
sleep "$SECONDS_TO_CAPTURE"
"$ADB" logcat -d -s Elm327:V ObdSession:V > "$OUT"

FRAME_COUNT=$(grep -c -- "->" "$OUT" || true)
echo "Wrote $OUT ($FRAME_COUNT adapter exchanges)"

if [ "$FRAME_COUNT" -eq 0 ]; then
    echo
    echo "No adapter traffic captured. Usual causes:" >&2
    echo "  - the app is not connected to the ELM327 (check the Live tab)" >&2
    echo "  - the car is asleep, so nothing answers" >&2
    echo "  - another OBD app holds the adapter (only one can connect at a time)" >&2
    exit 1
fi

echo
case "$MODE" in
    odometer) python3 tools/decode_capture.py "$OUT" --find-odometer "$KNOWN_VALUE" ;;
    speed)    python3 tools/decode_capture.py "$OUT" --find-speed "$KNOWN_VALUE" ;;
    *)        python3 tools/decode_capture.py "$OUT" ;;
esac
