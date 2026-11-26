---
name: phone-control
description: Control the Android phone this Debian runs on via Shizuku (rish); adb is only a fallback
metadata:
  type: reference
---

Claude runs in a proot Debian inside Haven on an Android phone.

**Primary: Shizuku.** `timeout 30 rish -c '<cmd>' </dev/null` runs with shell uid. Screenshot: `phone-shot file.png` (hides the status pill). `input`, `am`, `settings`, `pm` work; `uiautomator dump` does not. If Shizuku is not running, ask the user to open the Shizuku app and tap Start.

**Fallback: adb** on 127.0.0.1:5555 (`__REPO__/bin/phone-adb [wireless-port]`). Restarting adbd (`adb tcpip` / `adb usb`) kills the Shizuku server.

Details: `__REPO__/docs/`.
