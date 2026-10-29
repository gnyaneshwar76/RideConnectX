#!/bin/bash
# Records the REAL Google Maps navigation notification on the running emulator (fake GPS, Hyderabad).
# Usage: bash tools/maps-capture.sh <label>   e.g. A10
# Output: .claude/team/reports/maps-capture/<label>/{notif-*.txt, shade.png, maps.png, summary.txt}
export MSYS_NO_PATHCONV=1
A=/c/Users/eshwa/AppData/Local/Android/Sdk/platform-tools/adb.exe
M=com.google.android.apps.maps
OUT="$(dirname "$0")/../.claude/team/reports/maps-capture/$1"; mkdir -p "$OUT"

dump() { # label
  $A shell dumpsys notification --noredact | awk '/NotificationRecord\(/{p=($0 ~ /pkg=com.google.android.apps.maps/)} p' \
    | grep -E "android\.(title|text|subText|bigText|infoText|progress|progressMax|progressIndeterminate|shortCriticalText|template|largeIcon|requestPromotedOngoing)=|ongoingActivity|category=|channel=|flags=" \
    | sed 's/^ *//' | cut -c1-180 | sort -u > "$OUT/notif-$1.txt"
}
tapt() { # visible text -> tap
  $A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
  b=$($A shell cat /sdcard/u.xml | grep -o "<node[^>]*\(text\|content-desc\)=\"$1\"[^>]*>" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
  set -- $b; [ -n "$1" ] && $A shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) && return 0; return 1
}

$A shell svc power stayon true; $A shell input keyevent KEYCODE_WAKEUP; $A shell input keyevent 82
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION POST_NOTIFICATIONS; do $A shell pm grant $M android.permission.$p 2>/dev/null; done
$A emu geo fix 78.4867 17.3850 >/dev/null
$A shell "am start -a android.intent.action.VIEW -d 'google.navigation:q=Charminar,Hyderabad&mode=l' $M" >/dev/null 2>&1
sleep 12
# first-run screens (terms, sign-in, Location Accuracy, route preview) differ per Maps version: keep answering until a Maps notification exists
for round in $(seq 1 12); do
  for t in Skip "Got it" Dismiss "Turn on" "No thanks" "Not now" OK Start; do tapt "$t" && { echo "tapped $t"; sleep 3; }; done
  [ "$($A shell dumpsys notification --noredact | grep -c 'pkg=com.google.android.apps.maps')" -gt 0 ] && break
  $A shell "am start -a android.intent.action.VIEW -d 'google.navigation:q=Charminar,Hyderabad&mode=l' $M" >/dev/null 2>&1; sleep 25
done
sleep 5
dump start
$A shell screencap -p /sdcard/m.png; $A pull /sdcard/m.png "$OUT/maps.png" >/dev/null
lon=78.4867; lat=17.3850
for i in $(seq 1 24); do
  lon=$(awk "BEGIN{print $lon-0.0002}"); lat=$(awk "BEGIN{print $lat-0.0003}")
  $A emu geo fix $lon $lat >/dev/null; sleep 3
  [ $i = 6 ] && dump step6
  [ $i = 12 ] && dump step12
  [ $i = 24 ] && dump step24
done
$A shell cmd statusbar expand-notifications; sleep 2
$A shell screencap -p /sdcard/n.png; $A pull /sdcard/n.png "$OUT/shade.png" >/dev/null
$A shell cmd statusbar collapse
{
  echo "api=$($A shell getprop ro.build.version.sdk | tr -d '\r') maps=$($A shell dumpsys package $M | grep -m1 versionName | tr -d ' \r')"
  for f in start step6 step12 step24; do echo "--- $f"; cat "$OUT/notif-$f.txt"; done
} > "$OUT/summary.txt"
grep -c "" "$OUT/summary.txt"
