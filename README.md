# haven-claude-setup

Let a coding agent running **on** an Android phone control that same phone: the [Haven](https://github.com/GlassHaven/Haven) terminal → Debian (proot) → agent → [Shizuku](https://github.com/RikkaApps/Shizuku). **No root, no computer.**

Built with Claude Code. The core is agent-agnostic: see [connecting any agent](docs/integration.md).

## What is in here

| Directory | Contents |
|---|---|
| `pill/` | **Agent Pill**: a floating pill showing the agent's state with a live step timer (draggable, tucks into a screen edge) |
| `touchlock/` | Grabs the touchscreen while the agent operates it: your fingers are ignored, injected input still works |
| `bin/` | `phonectl` (the interface for any agent), `status.py` (Claude Code hooks), `phone-shot` (screenshot without the pill), `phone-adb` (adb reconnect), `ui-texts` |
| `adapters/` | Instruction snippet for any agent, OpenCode plugin example (untested) |
| `claude/` | Claude Code hook configuration, status line script, auto-start snippet, memory note templates |
| `docs/` | [Setup](docs/setup.md), [pill and touch lock](docs/pill.md), [connecting any agent](docs/integration.md), [pitfalls](docs/pitfalls.md) |
| `tests/` | Manual test scripts used during development (they really operate the screen; coordinates assume 1080×2400 portrait) |

## Install

Install and start Shizuku first ([docs/setup.md](docs/setup.md)), then:

```bash
git clone https://github.com/kurashizu/haven-claude-setup
cd haven-claude-setup
./install.sh                 # pill, touch lock, phonectl, Claude Code hooks
./install.sh --no-hooks      # core only, for other agents
./install.sh --statusline --autostart --memory   # optional extras
```

Restart your agent afterwards. The clone is the running copy, so do not move or delete it.

The [releases](https://github.com/kurashizu/haven-claude-setup/releases) carry a prebuilt APK and arm64 `touchlock` binary for reference. `install.sh` does not use them: it builds from source and signs with a key generated on your device. If you installed the release APK first, uninstall it before running the script.

## Security (read this first)

- While Shizuku is running, **any program** in this Debian can get an Android shell: screenshots, simulated taps, installing and removing apps, changing system settings.
- The touch lock disables your touchscreen. Safety nets: **any volume key unlocks immediately**, and each step unlocks by itself after at most 90 seconds. Check that the volume key works before relying on it.
- Shizuku's start-on-boot turns wireless debugging on by itself.
- For your own devices only. Provided as is, at your own risk.

## What has and has not been verified

Developed and used on a single Android 16 phone.

- Only Claude Code has been used with this. The OpenCode example is **untested**.
- **`install.sh` has not been run end to end on a fresh setup.**
- Shizuku starting by itself after a reboot: not verified.
- Tucking into the left edge, the vibration when tucking, and pressing the small round buttons while locked: not separately verified.
- In landscape the button hit areas may be off while locked (use a volume key).

## License

[MIT](LICENSE)
