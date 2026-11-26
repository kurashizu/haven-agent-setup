#!/usr/bin/python3 -I
"""Claude Code adapter for the status pill. Uses only documented interfaces:
hooks (https://code.claude.com/docs/en/hooks) and `claude agents --json`.

Usage: status.py hook               called by Claude Code, event JSON on stdin
       status.py watch SESSION_ID   started by the hook: no hook fires when the user interrupts

"Thinking" is reported only when a request is about to go to the model: after a prompt, and after a
batch of tool calls has resolved."""
import contextlib, fcntl, hashlib, json, os, re, subprocess, sys, tempfile, time

PHONECTL = os.path.join(os.path.dirname(os.path.realpath(__file__)), "phonectl")
# raw commands that drive the screen without going through phonectl (which locks by itself)
RAW_SHELL = re.compile(r"\brish\s+-c\b|\badb\b.*\bshell\b")
RAW_DRIVE = re.compile(r"\binput (tap|swipe|text|keyevent|motionevent|draganddrop)\b|\bam start |statusbar expand")
QUIET_SECONDS = 5  # only ask Claude Code for its status after this long without any hook


def phonectl(*args):
    return subprocess.run([PHONECTL, *args], stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                          stderr=subprocess.DEVNULL, timeout=22).returncode


@contextlib.contextmanager
def session_state(session):
    """Small per-session record shared by the hooks and the watcher. Locked while in use, which also
    keeps their reports in order."""
    name = hashlib.sha1(session.encode()).hexdigest()[:16]
    path = os.path.join(tempfile.gettempdir(), f"phone-pill-{os.getuid()}-{name}.json")
    with os.fdopen(os.open(path, os.O_RDWR | os.O_CREAT, 0o600), "r+") as f:
        fcntl.flock(f, fcntl.LOCK_EX)
        try:
            state = json.loads(f.read() or "{}")
        except ValueError:
            state = {}
        for key, default in (("label", "Thinking"), ("over", False), ("waiting", False),
                             ("seen", 0), ("watcher", 0)):
            state.setdefault(key, default)
        yield state
        f.seek(0)
        f.truncate()
        json.dump(state, f)


def claude_status(session):
    """`status` of this session from `claude agents --json`: busy, waiting or idle. None if unknown."""
    try:
        out = subprocess.run(["claude", "agents", "--json"], stdin=subprocess.DEVNULL, capture_output=True,
                             text=True, timeout=30).stdout
        return next((a.get("status") for a in json.loads(out) if a.get("sessionId") == session), None)
    except (OSError, ValueError, subprocess.TimeoutExpired):
        return None


def ensure_watcher(state, session):
    try:
        with open(f"/proc/{state['watcher']}/cmdline", "rb") as f:
            if b"watch\0" + session.encode() in f.read():
                return
    except OSError:
        pass
    state["watcher"] = subprocess.Popen(
        ["/usr/bin/python3", "-I", os.path.realpath(__file__), "watch", session], start_new_session=True,
        stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).pid


def watch(session, pause=QUIET_SECONDS):
    """Report what no hook tells us: the turn was interrupted, or a permission prompt was answered."""
    idle = unknown = 0
    while True:
        time.sleep(pause)
        with session_state(session) as state:
            if state["over"]:
                return
            if time.time() - state["seen"] < pause:  # hooks are active, nothing to find out
                idle = 0
                continue
            seen = state["seen"]
        status = claude_status(session)  # outside the lock: it takes a moment and hooks must not wait
        with session_state(session) as state:
            if state["over"]:
                return
            if state["seen"] != seen:
                idle = 0
                continue
            idle = idle + 1 if status == "idle" else 0
            unknown = unknown + 1 if status is None else 0
            if idle >= 2:  # twice in a row
                state["over"] = True
                phonectl("state", "idle")
                return
            if unknown >= 3:  # command unavailable or session gone: do not claim anything
                state["over"] = True
                phonectl("state", "off")
                return
            if status == "busy" and state["waiting"]:
                state["waiting"] = False
                phonectl("state", "working", state["label"])


def handle(d):
    event, tool, inp = d.get("hook_event_name"), d.get("tool_name", ""), d.get("tool_input") or {}
    session = d.get("session_id", "")
    with session_state(session) as state:
        state.update(seen=time.time(), waiting=False)

        def working(label):
            state.update(label=label, over=False)
            phonectl("state", "working", label)

        def waiting(label):
            state.update(waiting=True, over=False)
            phonectl("state", "waiting", label)

        def finished(*report):
            state["over"] = True
            phonectl("state", *report)

        if event == "UserPromptSubmit":
            state.update(label="Thinking", over=False)
            phonectl("state", "working", "Thinking", "--new-task")
        elif event == "PostToolBatch":
            working("Thinking")
        elif event == "PreCompact":
            working("Compacting context")
        elif event == "PostToolUseFailure" and d.get("is_interrupt"):
            finished("idle")
        elif event == "PermissionRequest":
            waiting(f"Permission: {tool}")
        elif event == "PreToolUse" and tool == "AskUserQuestion":
            waiting("Has a question for you")
        elif event == "PreToolUse":
            command = inp.get("command", "")
            label = inp.get("description") or tool.split("__")[-1]
            if RAW_SHELL.search(command) and RAW_DRIVE.search(command):
                state.update(label=label, over=False)
                if phonectl("lock", "--label", label) == 3:
                    print("The user took over the screen (pill button or volume key). Stop operating the "
                          "phone and ask before continuing.", file=sys.stderr)
                    return 2  # exit 2 blocks the tool call
            else:
                working(label)
        elif event == "Stop" and d.get("background_tasks"):
            finished("working", "Background task running")
        elif event == "Stop":
            finished("done")
        elif event == "StopFailure":
            finished("waiting", "Stopped by an error")
        elif event == "SessionEnd":
            finished("off")

        if not state["over"]:
            ensure_watcher(state, session)
    return 0


if __name__ == "__main__":
    try:
        if sys.argv[1:2] == ["watch"]:
            watch(sys.argv[2])
            sys.exit(0)
        sys.exit(handle(json.load(sys.stdin)) if sys.argv[1:] == ["hook"] else 0)
    except Exception:
        sys.exit(0)  # status display must never disturb the session
