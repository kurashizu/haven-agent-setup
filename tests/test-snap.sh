#!/bin/sh
# Small push past the right edge must stay open; a big push must tuck.
cd "$(dirname "$0")/.."
R() { timeout 40 rish -c "$1" </dev/null; }
RF=/sdcard/Android/data/com.local.claudepill/files/rects
bin/status.py work Snap test; sleep 3
drag() {  # drag by dx pixels, gripping the label area
  set -- $1 $(R "cat $RF")
  x=$(( $4 - 120 )); y=$(( ($5 + $7) / 2 ))
  R "input swipe $x $y $(( x + $1 )) $y 500"; sleep 2
}
set -- $(R "cat $RF"); echo "start:       $*"
# distance that leaves the pill 100px beyond the right edge (term x2 = 1065 means flush)
drag $(( 1065 - $9 + 100 )); echo "small push:  $(R "cat $RF")"
drag 420;                    echo "big push:    $(R "cat $RF")"
R 'screencap -p' > tests/tuck.png
