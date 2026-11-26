---
name: status-pill
description: Status pill and touch lock shown while the agent operates the phone
metadata:
  type: feedback
---

A floating pill shows your state, physical touch is blocked while you drive the screen, and the pill buttons always work. Hooks in the agent's settings drive it; code is in `__REPO__`.

Drive the screen with `phonectl tap|swipe|text|key|open` (it locks touch itself); `phonectl unlock` when finished. Raw `input` / `am start` commands are also detected by the hook, but not when hidden inside scripts. Taken over (button or volume key) means stop and ask. Switch back to Haven when done.

**Why:** the user cannot see the terminal while the phone is being driven.
**How to apply:** screenshot with `phonectl shot`; do not tap where the pill is.
