#!/bin/sh
# 30-second touch lock test; reports how the lock ended.
timeout 60 rish -c '
T=/data/local/tmp/touchlock
D=/sdcard/Android/data/com.local.claudepill/files
S="am start-foreground-service -n com.local.claudepill/.PillService --es state"
rm -f $D/override
$S touch >/dev/null
sleep 1
cat $D/rects; echo
$T lock 30
i=0
while [ $i -lt 32 ] && [ "$($T status)" = locked ]; do sleep 1; i=$((i+1)); done
echo "ended after ${i}s"
if [ -e $D/override ]; then echo "result: taken over by user"; else echo "result: timed out"; $S work >/dev/null; fi
$T unlock
dumpsys window | grep mCurrentFocus
' </dev/null
