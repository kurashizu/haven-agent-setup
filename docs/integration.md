# Connecting an agent

This page describes the interface. How an agent's events reach it is up to that agent: use whatever
hooks, plugins or instructions it officially provides.

```
agent ──(your adapter)──> phonectl ──rish──> pill app + touch lock
```

Install the core without the bundled hook adapter: `./install.sh --no-hooks`.

## 1. `phonectl`

| Command | Effect |
|---|---|
| `state working [text]` | Pill shows "Working" and the text, with a live timer. Keeps and renews an existing lock |
| `state waiting [text]` | "Your turn". Unlocks touch. Stays until the next state |
| `state done` | "Done". Unlocks; the pill disappears after 5 s and posts a notification |
| `state idle` | "Idle". Unlocks. Nothing is running and the task did not finish normally |
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

A lock lasts at most 90 s and is renewed by every driving command and `state working`.

## 2. What an adapter should report

| Moment | Call |
|---|---|
| the user gives a new instruction | `state working Thinking --new-task` |
| a tool or command starts | `state working <description>` |
| everything that was running has finished, the model is next | `state working Thinking` |
| permission or an answer is needed | `state waiting <what>` |
| the task finished | `state done` |
| interrupted or abandoned | `state idle` |
| the session closed | `state off` |

Rules:

- **Report the truth.** Say "Thinking" only while the model is what is being waited for. Tool calls can
  overlap, so do not report it just because one of them returned. If you cannot know the state, say `idle`
  or `off` rather than guess.
- Prefer documented, stable interfaces of your agent over its internal files or screen output.
- A status failure must never break the agent: ignore errors from `state`.
- Each call takes a noticeable fraction of a second. Updates that arrive out of order are discarded by
  the app, so background reporting is fine.
- If the agent can run raw `input` / `am start` commands, either forbid that, or call `phonectl lock`
  first and **block the command** on exit code 3.
- If there is no event for the end of a task, call `phonectl unlock` after operating the screen.

An agent that cannot report through hooks can follow [agent-instructions.md](agent-instructions.md).

## 3. Low-level protocol (to replace `phonectl`)

All commands run as the Android `shell` user (through `rish` or `adb shell`).

**Pill service** (protected by `android.permission.DUMP`):

```
am start-foreground-service -n com.local.claudepill/.PillService --es state <state> [--el seq <n>] [--es b64 <text>]
```

- `state`: `work`, `touch`, `wait`, `done`, `idle`, `taken`, `off`, and `hide` / `show` for screenshots
- `seq`: increasing number (nanoseconds work); older updates are ignored
- `b64`: base64 of the UTF-8 detail text

The `touch` state only changes the look and lets injected taps pass through the pill. It does not lock.

**Touch lock:** `/data/local/tmp/touchlock lock <seconds ≤120> | unlock | status`.
`lock` exits 3 while the take-over marker exists. Volume keys and the pill buttons release it.

**Files** in `/sdcard/Android/data/com.local.claudepill/files/`:

- `override`: the take-over marker
- `rects`: button positions, written by the app for `touchlock`

## 4. Limits

- One agent at a time. Two agents share one pill, one lock and one take-over marker, and would overwrite
  each other.
- The take-over marker stops `phonectl` and the lock. It cannot stop an agent that calls `input` directly.
- The terminal button opens Haven (`sh.haven.app`). For another terminal change the package name in
  `PillService.java`, `AndroidManifest.xml` (`<queries>`) and `touchlock.c`, then rebuild.
- The package id keeps its original name, `com.local.claudepill`, so existing installs can be updated.
