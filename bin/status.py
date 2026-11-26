#!/usr/bin/python3 -I
"""Claude Code -> phone status pill + touch lock.
Hook mode: status.py hook (JSON on stdin). Manual: status.py <work|touch|wait|done|off> [text]
'touch' locks the touchscreen (max 90s per step); wait/done/off unlock it."""
import base64, json, re, shlex, subprocess, sys, time

LOCK = "/data/local/tmp/touchlock"
OVER = "/sdcard/Android/data/com.local.claudepill/files/override"

def send(state, text="", new_prompt=False):
    b64 = base64.b64encode((text if len(text) <= 120 else text[:117] + "...").encode()).decode()
    am = ("am start-foreground-service -n com.local.claudepill/.PillService "
          f"--es state \"$S\" --el seq {time.time_ns()}" + (f" --es b64 {shlex.quote(b64)}" if b64 else ""))
    sh = f"S={state}; "
    if new_prompt:
        sh += f"rm -f {OVER}; "
    if state == "touch":      # lock; fail if the user took over with a volume key
        sh += f"{LOCK} lock 90 >/dev/null || exit 3; "
    elif state == "work":     # still mid-operation: renew the lock and keep showing it
        sh += f"[ -e {OVER} ] && S=taken; "
        sh += f"[ \"$({LOCK} status)\" = locked ] && {LOCK} lock 90 >/dev/null && S=touch; "
    else:
        sh += f"{LOCK} unlock >/dev/null; "
    sh += am + " >/dev/null"
    return subprocess.run(["rish", "-c", sh], stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                          stderr=subprocess.DEVNULL, timeout=20).returncode

def from_hook(d):
    ev, tool, inp = d.get("hook_event_name"), d.get("tool_name", ""), d.get("tool_input") or {}
    if ev == "UserPromptSubmit": return "work", "Thinking"
    if ev == "Stop": return "done", ""
    if ev == "SessionEnd": return "off", ""
    if ev == "PermissionRequest": return "wait", f"Permission: {tool}"
    if ev == "PreToolUse":
        if tool == "AskUserQuestion": return "wait", "Has a question for you"
        cmd = inp.get("command", "")
        drives = (re.search(r"\brish\s+-c\b|\badb\b.*\bshell\b", cmd) and
                  re.search(r"\binput (tap|swipe|text|keyevent|motionevent|draganddrop)\b|\bam start |statusbar expand", cmd))
        return ("touch" if drives else "work"), inp.get("description") or tool.split("__")[-1]
    return None

try:
    if sys.argv[1] == "hook":
        d = json.load(sys.stdin)
        r = from_hook(d)
        if r:
            rc = send(*r, new_prompt=d.get("hook_event_name") == "UserPromptSubmit")
            if r[0] == "touch" and rc == 3:
                print("The user took over the screen (pill button or volume key). Stop operating the phone "
                      "and ask before continuing.", file=sys.stderr)
                sys.exit(2)
    else:
        send(sys.argv[1], " ".join(sys.argv[2:]))
except Exception:
    pass  # status display must never disturb the session
