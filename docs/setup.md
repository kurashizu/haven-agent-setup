# Setup

Requirements: Android 11+ (13+ for start-on-boot), Debian installed in Haven, Claude Code installed in Debian.

## 1. Wireless debugging (adb to the phone itself)

1. Settings → About phone → tap "Build number" repeatedly to enable developer options.
2. Turn on "Wireless debugging".
3. `apt install adb`
4. Tap "Pair device with pairing code" (use split screen: closing the dialog invalidates the code):
   ```bash
   adb pair 127.0.0.1:<pairing-port> <code>
   adb connect 127.0.0.1:<port-on-main-page>     # the two ports differ
   ```
5. Optional: enable "Disable ADB authorisation timeout" in developer options.

`bin/phone-adb <port>` connects, switches to the fixed port 5555 and restarts Shizuku if needed.
Port 5555 is visible to your network (an authorised key is still required) and is gone after a reboot.

## 2. Shizuku

1. Install it from the [official releases](https://github.com/RikkaApps/Shizuku/releases) (13.6.0 was used here).
2. In Shizuku tap "Pairing", enter the pairing code in its notification, then tap "Start".
3. Start-on-boot without root needs all of: Android 13+, a trusted Wi-Fi connection, Shizuku itself paired, last start done through adb, the start-on-boot setting on, and:
   ```bash
   adb shell pm grant moe.shizuku.privileged.api android.permission.WRITE_SECURE_SETTINGS
   ```
4. Haven's terminal ships with `rish`. Check it: `rish -c id` should print `uid=2000(shell)`.

## 3. This repository

`./install.sh`, see the README.

## 4. Optional: Haven MCP

Haven serves MCP locally (notifications, sensors, terminal sessions, file transfer and more):

```bash
claude mcp add --transport http -s user haven http://127.0.0.1:8730/mcp
```

It grants broad access (mail, TOTP codes, camera, location). Add it only if you need it.

## Undo

```bash
rish -c '/data/local/tmp/touchlock unlock; pm uninstall com.local.claudepill'
rish -c 'settings delete global adb_allowed_connection_time'
claude mcp remove -s user haven
```

Then remove the hooks from `~/.claude/settings.json` (the installer saves a backup as `settings.json.bak`).
