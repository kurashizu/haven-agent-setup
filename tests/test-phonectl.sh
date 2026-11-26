#!/bin/sh
# Exercises phonectl and the Claude Code adapter. Locks touch for a few seconds.
cd "$(dirname "$0")/.."
P=bin/phonectl
O=/sdcard/Android/data/com.local.claudepill/files/override
R() { timeout 40 rish -c "$1" </dev/null; }
check() { printf '%-34s %s\n' "$1" "$2"; }
R "rm -f $O"
$P state working "phonectl test"; check "state working" "rc=$?"
$P swipe 540 900 540 902 100; check "swipe (locks)" "rc=$?  $($P status | tr '\n' ' ')"
$P state working "still locked"; check "state keeps lock" "$($P status | head -1)"
$P shot tests/shot.png; check "shot" "rc=$? $(head -c 4 tests/shot.png | tail -c 3)"
$P state waiting "question"; check "waiting unlocks" "$($P status | head -1)"
$P key 'x;reboot' >/dev/null 2>&1; check "bad key rejected" "rc=$?"
$P open com.android.settings --label "open test"; check "open package" "rc=$?"
sleep 1; check "focused" "$(R 'dumpsys window | grep -m1 mCurrentFocus' | sed 's/.* //')"
$P open sh.haven.app; check "back to terminal" "rc=$?"
$P unlock
R "touch $O"
$P tap 540 900 2>/dev/null; check "tap after take-over (want 3)" "rc=$?"
echo '{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"rish -c \"input tap 1 1\""}}' \
  | bin/status.py hook 2>/dev/null; check "hook blocks raw tap (want 2)" "rc=$?"
$P state working "new prompt" --new-task; check "new task clears take-over" "$($P status | tail -1)"
$P state done; check "done" "rc=$?  $($P status | head -1)"
rm -f tests/shot.png
