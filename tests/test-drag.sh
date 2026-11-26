#!/bin/sh
# Drag the pill down by its label area, check the buttons moved, then drag it back.
R() { timeout 40 rish -c "$1" </dev/null; }
RF=/sdcard/Android/data/com.local.claudepill/files/rects
"$(dirname "$0")/../bin/status.py" work Drag test; sleep 2
echo "before: $(R "cat $RF")"
set -- $(R "cat $RF")
x=$(( $3 - 250 )); y=$(( ($4 + $6) / 2 ))
R "input swipe $x $y $x $((y + 800)) 600"; sleep 2
echo "after:  $(R "cat $RF")"
R "input swipe $x $((y + 800)) $x $y 600"; sleep 2
echo "back:   $(R "cat $RF")"
