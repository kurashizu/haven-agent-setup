#!/bin/sh
# Drag the pill past the right edge (tuck), screenshot, then tap the tab (expand).
cd "$(dirname "$0")/.."
R() { timeout 40 rish -c "$1" </dev/null; }
RF=/sdcard/Android/data/com.local.claudepill/files/rects
bin/status.py work Dock test; sleep 2
set -- $(R "cat $RF"); echo "start:    $*"
x=$(( $3 - 200 )); y=$(( ($4 + $6) / 2 ))
R "input swipe $x $y $((x + 600)) $((y + 700)) 500"; sleep 2
echo "tucked:   $(R "cat $RF")"
R 'screencap -p' > tests/dock.png
# tab: 34dp wide at the right edge, below the dragged-to position
R "input tap 1040 $((140 - 20 + 700 + 40))"; sleep 2
echo "expanded: $(R "cat $RF")"
R 'screencap -p' > tests/expand.png
