#!/usr/bin/env bash
# Hold ONE maneuver code on the cluster until stopped.
#
#   tools/sweep-hold.sh 19
#
# Why it repeats: the cluster clears the arrow a second or two after the last
# packet, so a single broadcast flashes and vanishes before the rider can look.
# Re-sending every 800ms keeps the icon up indefinitely.
#
# Why dist == code: the distance field prints on the cluster itself, so the
# screen labels its own code number. Every photograph is then self-identifying
# and an answer can never be matched to the wrong code. Getting this wrong is
# exactly what invalidated the 4 August sweep.
#
# Codes 31-45 are LOCKED (photographed 11 August) — skip them.

set -u
CODE="${1:?usage: sweep-hold.sh <code> [dist]}"
DIST="${2:-$CODE}"
ADB="${LOCALAPPDATA}/Android/Sdk/platform-tools/adb.exe"
CMP="com.gnyaneshwar.rideconnectx/com.eshwar.rideconnectx.debug.NavTestReceiver"

echo "holding code=$CODE dist=$DIST — Ctrl-C to stop"
while true; do
  "$ADB" shell "am broadcast -n $CMP -a com.eshwar.rideconnectx.TEST_CODE --ei code $CODE --ei dist $DIST" >/dev/null 2>&1
  sleep 0.8
done
