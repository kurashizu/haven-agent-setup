#!/bin/sh
# Tuck into the left edge, check it auto-expands in the locked state (display only, no real lock).
cd "$(dirname "$0")/.."
R() { timeout 40 rish -c "$1" </dev/null; }
RF=/sdcard/Android/data/com.local.claudepill/files/rects
S='am start-foreground-service -n com.local.claudepill/.PillService --es state'
bin/status.py work Dock test; sleep 2
set -- $(R "cat $RF"); x=$(( $3 - 200 )); y=$(( ($4 + $6) / 2 ))
R "input swipe $x $y 5 $y 500"; sleep 2
echo "tucked left:  $(R "cat $RF")"
R "$S touch >/dev/null"; sleep 2
echo "locked look:  $(R "cat $RF")"
R "$S work >/dev/null"; sleep 2
echo "tucked again: $(R "cat $RF")"
# tap the tab so the user gets the full pill back
R "input tap 40 $y"; sleep 2
echo "expanded:     $(R "cat $RF")"
R '/data/local/tmp/touchlock status'
