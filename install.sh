#!/bin/bash
# Install the whole setup inside Haven's Debian proot. Shizuku must already be running (docs/setup.md).
#   ./install.sh [--no-hooks] [--statusline] [--autostart] [--memory]
# --no-hooks installs only the agent-agnostic core (pill, touch lock, phonectl); see docs/integration.md.
# The other options belong to the bundled hook adapter.
set -euo pipefail
REPO=$(cd "$(dirname "$0")" && pwd)
PKG=com.local.claudepill
T=/data/local/tmp/touchlock
HOOKS=1 STATUSLINE=0 AUTOSTART=0 MEMORY=0
for a in "$@"; do
  case $a in
    --no-hooks) HOOKS=0 ;;
    --statusline) STATUSLINE=1 ;;
    --autostart) AUTOSTART=1 ;;
    --memory) MEMORY=1 ;;
    *) echo "unknown option: $a" >&2; exit 2 ;;
  esac
done
R() { timeout 90 rish -c "$1" </dev/null; }
step() { printf '\n== %s\n' "$1"; }

step "check Shizuku"
R id | grep -q 'uid=2000' || { echo "rish does not work: start Shizuku first (docs/setup.md)" >&2; exit 1; }

step "install packages"
apt-get install -y -qq default-jdk-headless aapt apksigner zipalign unzip zip gcc libc6-dev curl python3

step "build"
sh "$REPO/pill/build.sh"
mkdir -p "$REPO/touchlock/out"
gcc -static -O2 -Wall -o "$REPO/touchlock/out/touchlock" "$REPO/touchlock/touchlock.c"

step "install app"
APK=$REPO/pill/out/claudepill.apk
# a different signing key than an earlier install makes this fail: uninstall the old app first
timeout 120 rish -c "pm install -r -S $(stat -c %s "$APK")" < "$APK"
R "appops set $PKG SYSTEM_ALERT_WINDOW allow; pm grant $PKG android.permission.POST_NOTIFICATIONS; cmd deviceidle whitelist +$PKG"

step "push touch lock"
BIN=$REPO/touchlock/out/touchlock
# a running copy keeps the file busy and the overwrite fails silently: unlock, remove, verify
R "[ -x $T ] && $T unlock >/dev/null; rm -f $T" || true
timeout 90 rish -c "cat > $T && chmod 755 $T" < "$BIN"
want=$(md5sum "$BIN" | cut -d' ' -f1)
R "md5sum $T" | grep -q "$want" || { echo "touchlock was not updated" >&2; exit 1; }

step "commands"
chmod +x "$REPO"/bin/*
ln -sf "$REPO/bin/phonectl" /usr/local/bin/phonectl
ln -sf "$REPO/bin/phone-shot" /usr/local/bin/phone-shot

if [ $HOOKS = 1 ]; then
step "hook adapter"
mkdir -p "$HOME/.claude"
python3 -I - "$REPO" "$STATUSLINE" <<'EOF'
import json, os, shutil, sys
repo, statusline = sys.argv[1], sys.argv[2] == "1"
path = os.path.expanduser("~/.claude/settings.json")
cfg = {}
if os.path.exists(path):
    shutil.copy(path, path + ".bak")
    cfg = json.load(open(path))
template = json.loads(open(os.path.join(repo, "claude/hooks.json")).read().replace("__REPO__", repo))
hooks = cfg.setdefault("hooks", {})
for event, entries in template.items():
    # drop earlier copies of our hook, keep everything else
    kept = [e for e in hooks.get(event, [])
            if not any(h.get("command", "").endswith("status.py hook") for h in e.get("hooks", []))]
    hooks[event] = kept + entries
if statusline:
    cfg["statusLine"] = {"type": "command", "command": f"bash {repo}/claude/statusline-command.sh"}
json.dump(cfg, open(path, "w"), indent=2)
EOF
fi

if [ $AUTOSTART = 1 ] && ! grep -q '# auto-start claude' "$HOME/.profile" 2>/dev/null; then
  step "auto-start claude"
  cat "$REPO/claude/profile-autostart.sh" >> "$HOME/.profile"
fi

if [ $MEMORY = 1 ]; then
  step "memory notes"
  M="$HOME/.claude/projects/$(echo "$HOME" | tr / -)/memory"
  mkdir -p "$M"
  for f in "$REPO"/claude/memory/*.md; do
    n=$(basename "$f")
    [ -e "$M/$n" ] && { echo "kept existing $n"; continue; }
    sed "s#__REPO__#$REPO#g" "$f" > "$M/$n"
    echo "- [${n%.md}]($n)" >> "$M/MEMORY.md"
  done
fi

printf '\nDone. Restart your agent.\n'
