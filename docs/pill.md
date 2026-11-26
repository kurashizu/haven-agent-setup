# Pill and touch lock

## States

| Pill | Touchscreen | Buttons |
|---|---|---|
| Yellow `Working` | normal | `❚❚` take over, `>_` open terminal |
| Red `Touch locked` | only the pill buttons respond | `❚❚`, `>_` |
| Purple `Taken over` | normal; the agent can no longer tap | `▶` hand back, `>_` |
| Blue `Your turn` | normal | `>_` |
| Green `Done` | normal | `>_`; disappears after 5 s and posts a notification |
| Grey `Idle` | normal | `>_`; the turn ended without finishing, e.g. interrupted |

Sending a new prompt clears "taken over".

## Gestures

- Drag anywhere except a button; the position is remembered.
- Push more than a third of the pill past a side edge and let go: it tucks into a small tab. Tap it or pull it inwards to open.
- It opens by itself while touch is locked.

## How it works

- Agents call `bin/phonectl`, which sends the state to the app through `rish`. The app's service is protected by the `DUMP` permission, so ordinary apps cannot drive it.
- The pill times the current step; the timer restarts when the step description changes.
- The bundled hook adapter also treats a command containing `rish -c` or `adb shell` together with `input …` or `am start ` as screen driving: it locks touch first and, after you take over, blocks the command.
- `touchlock` grabs the touchscreen with `EVIOCGRAB` (shell is in the `input` group) and reads raw coordinates, reacting only to the pill buttons. Simulated taps are injected by the system and are unaffected.
- The app writes its button positions to its own external directory; `touchlock` reads them on every touch.
- While locked the window is not touchable (alpha ≤ 0.8), so injected taps pass through it.
