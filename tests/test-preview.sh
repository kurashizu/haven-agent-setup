#!/bin/sh
# Open the tucked tab (it sits on the right edge), centre the pill and screenshot it.
cd "$(dirname "$0")/.."
R() { timeout 40 rish -c "$1" </dev/null; }
RF=/sdcard/Android/data/com.local.claudepill/files/rects
bin/phonectl state working "Preview of the narrower pill with a longer summary line"; sleep 2
if [ "$(R "cat $RF" | cut -d' ' -f1)" = 0 ]; then
  y=$(R 'dumpsys window windows' | grep -A12 'com.local.claudepill' | grep -oE 'frame=\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1)
  set -- $(echo "$y" | tr -c '0-9' ' ')
  R "input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"; sleep 2
fi
set -- $(R "cat $RF"); echo "rects: $*"
if [ "$1" != 0 ]; then
  x=$(( $3 - 100 )); y=$(( ($4 + $6) / 2 ))
  # glide to the horizontal centre
  right=$(( $9 + 15 )); left=$(( $3 - 200 ))
  R "input swipe $x $y $(( x - (right - 1080) / 2 - 150 )) $y 500"; sleep 2
fi
echo "after: $(R "cat $RF")"
R 'screencap -p' > tests/preview.png
