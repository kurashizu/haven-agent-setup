#!/bin/sh
# Leave Haven, tap the pill's Terminal button, and report which window ends up focused.
R() { timeout 40 rish -c "$1" </dev/null; }
R 'input keyevent HOME; sleep 2; dumpsys window | grep mCurrentFocus'
"$(dirname "$0")/../bin/phonectl" state working Testing Terminal button
sleep 1
set -- $(R 'cat /sdcard/Android/data/com.local.claudepill/files/rects')
x=$(( (${7} + ${9}) / 2 )); y=$(( (${8} + ${10}) / 2 ))
echo "tap $x $y"
R "logcat -c; input tap $x $y; sleep 2; logcat -d | grep -iE 'claudepill|Background activity' | grep -iE 'START|block|denied|background' | tail -5; dumpsys window | grep mCurrentFocus"
# always come back, even if the button failed
R 'am start -n sh.haven.app/.MainActivity >/dev/null 2>&1'
