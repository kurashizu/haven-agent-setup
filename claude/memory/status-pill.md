---
name: status-pill
description: Status pill and touch lock shown while Claude operates the phone
metadata:
  type: feedback
---

A floating pill shows Claude's state, physical touch is blocked while Claude drives the screen, and the pill buttons always work. Hooks in `~/.claude/settings.json` drive it; code is in `__REPO__`.

Touch locks when a Bash command with `rish -c` / `adb shell` contains `input ...` or `am start `. For taps hidden inside scripts run `__REPO__/bin/status.py touch` first. Taken over (button or volume key) means stop and ask. Switch back to Haven when done.

**Why:** the user cannot see the terminal while the phone is being driven.
**How to apply:** screenshot with `phone-shot`; do not tap where the pill is.
