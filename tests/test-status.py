#!/usr/bin/python3 -I
"""Offline test of the hook adapter's logic (phonectl and the agent's status are faked)."""
import importlib.machinery, importlib.util, os, sys

sys.dont_write_bytecode = True
path = os.path.join(os.path.dirname(os.path.realpath(__file__)), "..", "bin", "status.py")
loader = importlib.machinery.SourceFileLoader("status", path)
st = importlib.util.module_from_spec(importlib.util.spec_from_loader("status", loader))
loader.exec_module(st)

SESSION = f"test-{os.getpid()}"
seen = []
st.phonectl = lambda *a: seen.append(" ".join(a[1:]).replace(" --new-task", "") if a[0] == "state" else "lock") or 0
st.ensure_watcher = lambda *a: None
failed = 0


def ev(name, tool="Bash", label=None, **extra):
    st.handle({"hook_event_name": name, "session_id": SESSION, "tool_name": tool,
               "tool_input": {"description": label, "command": "x"}, **extra})


def check(title, want):
    global failed
    got, seen[:] = list(seen), []
    failed += got != want
    print("ok  " if got == want else "FAIL", title, "" if got == want else f"got {got} want {want}")


def watch(*statuses):
    values = iter(statuses)
    st.agent_status = lambda s: next(values, "idle")
    st.watch(SESSION, pause=0)


ev("UserPromptSubmit")
ev("PreToolUse", label="Build"); ev("PreToolUse", label="Test")
check("tools are named while they run", ["working Thinking", "working Build", "working Test"])
ev("PostToolBatch")
check("thinking only once the whole batch is done", ["working Thinking"])
ev("PreToolUse", label="Edit"); ev("PermissionRequest")
watch("waiting", "busy", "idle", "idle")
check("approved, then interrupted", ["working Edit", "waiting Permission: Bash", "working Edit", "idle"])
ev("UserPromptSubmit"); watch("busy", "idle", "busy", "idle", "idle")
check("a single idle reading is not enough", ["working Thinking", "idle"])
ev("UserPromptSubmit"); watch(None, None, None)
check("status unavailable: claim nothing", ["working Thinking", "off"])
ev("UserPromptSubmit"); ev("Stop"); watch("idle", "idle")
check("a finished turn stays done", ["working Thinking", "done"])
ev("Stop", background_tasks=[{"id": "1"}])
check("background task still running", ["working Background task running"])
ev("PostToolUseFailure", is_interrupt=True)
check("interrupted tool", ["idle"])
sys.exit(1 if failed else 0)
