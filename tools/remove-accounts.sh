#!/bin/bash
# Removes every account on the running emulator through the Settings screens (never types credentials).
# Usage: bash tools/remove-accounts.sh   -> prints "accounts left: N"
export MSYS_NO_PATHCONV=1
A=/c/Users/eshwa/AppData/Local/Android/Sdk/platform-tools/adb.exe
dump() { $A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; $A shell cat /sdcard/u.xml; }
# tap the first node whose text matches (whole text); $2 = "last" taps the last match
tapt() {
  b=$(dump | grep -o "<node[^>]*text=\"$1\"[^>]*>" | { [ "$2" = last ] && tail -1 || head -1; } | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
  set -- $b; [ -n "$1" ] && $A shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )) && return 0; return 1
}
left() { $A shell dumpsys account | grep -c 'Account {name='; }

for round in 1 2 3; do
  [ "$(left)" = 0 ] && break
  $A shell am start -a android.settings.SYNC_SETTINGS >/dev/null 2>&1; sleep 3
  row=$(dump | grep -o 'text="[^"]*@[^"]*"' | head -1 | sed 's/^text="//; s/"$//')
  [ -z "$row" ] && break
  tapt "$row" || break; sleep 3
  tapt "Remove account" || { $A shell input swipe 540 1800 540 600 300; sleep 1; tapt "Remove account"; }
  sleep 3
  tapt "Remove account" last; sleep 4
done
echo "accounts left: $(left)"
