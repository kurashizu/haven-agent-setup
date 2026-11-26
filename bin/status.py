#!/usr/bin/python3 -I
"""Claude Code adapter: translates hook events (JSON on stdin) into phonectl calls.
Usage: status.py hook"""
import json, os, re, subprocess, sys

PHONECTL = os.path.join(os.path.dirname(os.path.realpath(__file__)), "phonectl")
# raw commands that drive the screen without going through phonectl (which locks by itself)
RAW_SHELL = re.compile(r"\brish\s+-c\b|\badb\b.*\bshell\b")
RAW_DRIVE = re.compile(r"\binput (tap|swipe|text|keyevent|motionevent|draganddrop)\b|\bam start |statusbar expand")


def phonectl(*args):
    return subprocess.run([PHONECTL, *args], stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                          stderr=subprocess.DEVNULL, timeout=22).returncode


def handle(d):
    ev, tool, inp = d.get("hook_event_name"), d.get("tool_name", ""), d.get("tool_input") or {}
    if ev == "UserPromptSubmit":
        phonectl("state", "working", "Thinking", "--new-task")
    elif ev == "PostToolUse":
        phonectl("state", "working", "Thinking")
    elif ev == "PermissionRequest":
        phonectl("state", "waiting", f"Permission: {tool}")
    elif ev == "Stop":
        phonectl("state", "done")
    elif ev == "SessionEnd":
        phonectl("state", "off")
    elif ev == "PreToolUse":
        if tool == "AskUserQuestion":
            phonectl("state", "waiting", "Has a question for you")
            return 0
        command = inp.get("command", "")
        label = inp.get("description") or tool.split("__")[-1]
        if RAW_SHELL.search(command) and RAW_DRIVE.search(command):
            if phonectl("lock", "--label", label) == 3:
                print("The user took over the screen (pill button or volume key). Stop operating the phone "
                      "and ask before continuing.", file=sys.stderr)
                return 2  # Claude Code: exit 2 blocks the tool call
        else:
            phonectl("state", "working", label)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(handle(json.load(sys.stdin)) if sys.argv[1:] == ["hook"] else 0)
    except Exception:
        sys.exit(0)  # status display must never disturb the session
