# Connecting any agent

Everything an agent needs is the `phonectl` command. Hooks or plugins are optional sugar.

```
agent ──(hooks / plugin / instructions)──> phonectl ──rish──> pill app + touchlock
```

| Adapter | Status |
|---|---|
| Claude Code (`bin/status.py`, `claude/hooks.json`) | in daily use |
| Any agent through instructions (`adapters/generic/AGENTS.md`) | the commands are tested, no agent was tested with this file |
| OpenCode (`adapters/opencode/`) | **untested** |
| Antigravity CLI, others | not written; use the tables below |

Install the core without Claude Code hooks: `./install.sh --no-hooks`.

## 1. `phonectl`

| Command | Effect |
|---|---|
| `state working [text]` | Pill shows "Working" and the text, with a live timer. Keeps and renews an existing lock |
| `state waiting [text]` | "Your turn". Unlocks touch. Stays until the next state |
| `state done` | "Done". Unlocks; the pill disappears after 5 s and posts a notification |
| `state off` | Removes the pill and unlocks |
| `... --new-task` | Also clears "taken over". Only when the **user** has just given a new instruction |
| `tap X Y` | Lock, show, inject a tap (screen pixels) |
| `swipe X1 Y1 X2 Y2 [MS]` | Same for a swipe |
| `text STRING` | Type ASCII text into the focused field |
| `key KEYCODE` | For example `BACK`, `HOME`, `ENTER` |
| `open PACKAGE[/ACTIVITY]` | Start an app |
| `lock` | Lock only, for driving the screen by other means |
| `unlock` | Release the lock (the take-over marker stays) |
| `status` | Prints `locked`/`unlocked` and `taken-over`/`agent-allowed` |
| `shot FILE.png` | Screenshot without the pill |

Driving commands and `lock` accept `--label TEXT`, shown in the pill.

**Exit codes:** `0` ok · `1` failed · `2` usage · `3` **the user has taken over: stop and ask**.

A lock lasts at most 90 s and is renewed by every driving command and `state working`. An agent without a
"finished" event should call `phonectl unlock` itself.

## 2. Mapping agent events

| Agent event | Call | Claude Code | OpenCode V2 (untested) |
|---|---|---|---|
| user sends a prompt | `state working Thinking --new-task` | `UserPromptSubmit` | `session.execution.started` |
| before a tool runs | `state working <description>` | `PreToolUse` | `tool.hook("execute.before")` |
| after a tool ran | `state working Thinking` | `PostToolUse` | `tool.hook("execute.after")` |
| needs permission or an answer | `state waiting <what>` | `PermissionRequest`, `AskUserQuestion` | `permission.asked`, `form.created` |
| turn finished | `state done` | `Stop` | `session.execution.succeeded` / `failed` / `interrupted` |
| session closed | `state off` | `SessionEnd` | - |

Guidelines for an adapter:

- Never let a status failure break the agent: ignore errors from `state`.
- Update in the background where the agent allows it. Each call takes a noticeable fraction of a second;
  out-of-order updates are discarded by the app.
- If the agent runs raw `input` / `am start` commands, either forbid that or call `phonectl lock` before
  the command and **block the tool call** on exit code 3. `bin/status.py` is a reference implementation.
  The OpenCode example does not do this: use `phonectl` for driving there.

## 3. Antigravity CLI and other hook-based agents

Third-party guides describe a `hooks.json` with `PreToolUse` and `Stop` events for `agy`. We have **not**
verified the file location, input format or `Stop` semantics, so no file is shipped. Check your agent's own
documentation, then apply the table above. If an agent has only some events, wire those and put
`adapters/generic/AGENTS.md` in its instructions for the rest.

## 4. Low-level protocol (to replace `phonectl`)

All commands run as the Android `shell` user (through `rish` or `adb shell`).

**Pill service** (protected by `android.permission.DUMP`):

```
am start-foreground-service -n com.local.claudepill/.PillService --es state <state> [--el seq <n>] [--es b64 <text>]
```

- `state`: `work`, `touch`, `wait`, `done`, `taken`, `off`, and `hide` / `show` for screenshots
- `seq`: increasing number (nanoseconds work); older updates are ignored
- `b64`: base64 of the UTF-8 detail text

The `touch` state only changes the look and lets injected taps pass through the pill. It does not lock.

**Touch lock:** `/data/local/tmp/touchlock lock <seconds ≤120> | unlock | status`.
`lock` exits 3 while the take-over marker exists. Volume keys and the pill buttons release it.

**Files** in `/sdcard/Android/data/com.local.claudepill/files/`:

- `override`: the take-over marker
- `rects`: button positions, written by the app for `touchlock`

## 5. Limits

- One agent at a time. Two agents share one pill, one lock and one take-over marker, and would overwrite
  each other.
- The take-over marker stops `phonectl` and the lock. It cannot stop an agent that calls `input` directly.
- The terminal button opens Haven (`sh.haven.app`). For another terminal change the package name in
  `PillService.java`, `AndroidManifest.xml` (`<queries>`) and `touchlock.c`, then rebuild.
- The package id keeps its original name, `com.local.claudepill`, so existing installs can be updated.
