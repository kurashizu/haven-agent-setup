#!/usr/bin/env bash
# Claude Code status line: model, effort, context left, 5h/7d usage, cwd, git branch

input=$(cat)

IFS=$'\x1f' read -r model effort ctx five week cwd five_at week_at < <(
  printf '%s' "$input" | PYTHONIOENCODING=utf-8 /usr/bin/python3 -I -X utf8 -c '
import json, sys

data = json.load(sys.stdin)

def get(path):
    v = data
    for key in path.split("."):
        v = v.get(key) if isinstance(v, dict) else None
    return "" if v is None else "".join(c for c in str(v) if c.isprintable())

print("\x1f".join([
    get("model.display_name"),
    get("effort.level"),
    get("context_window.remaining_percentage"),
    get("rate_limits.five_hour.used_percentage"),
    get("rate_limits.seven_day.used_percentage"),
    get("workspace.current_dir") or get("cwd"),
    get("rate_limits.five_hour.resets_at"),
    get("rate_limits.seven_day.resets_at"),
]))
'
)

# pi-style footer: dim text, color only where it carries meaning
rgb() { printf '\033[38;2;%s;%s;%sm' "$1" "$2" "$3"; }
reset=$'\033[0m'
gray=$(rgb 110 115 141)
purple=$(rgb 187 154 247)
pink=$(rgb 255 121 198)
blue=$(rgb 122 162 247)
cyan=$(rgb 125 207 255)
green=$(rgb 158 206 106)
yellow=$(rgb 224 175 104)
red=$(rgb 247 118 142)

# color by percentage used: <50 green, <80 yellow, else red
level() {
  if [ "$1" -ge 80 ]; then printf '%s' "$red"
  elif [ "$1" -ge 50 ]; then printf '%s' "$yellow"
  else printf '%s' "$green"
  fi
}
join() { printf '%s' "${1:+$1${2:+ ${gray}·${reset} }}$2"; }

# time left until an epoch: 3d4h, 2h13m, 8m
left() {
  local s=$(( ${1%.*} - $(date +%s) ))
  [ "$s" -lt 0 ] && s=0
  if [ "$s" -ge 86400 ]; then printf '%dd%dh' $((s / 86400)) $((s % 86400 / 3600))
  elif [ "$s" -ge 3600 ]; then printf '%dh%dm' $((s / 3600)) $((s % 3600 / 60))
  else printf '%dm' $((s / 60))
  fi
}

# usage <label> <used%> <reset epoch>
usage() {
  [ -n "$2" ] || return 0
  local n at=""
  n=$(LC_ALL=C printf '%.0f' "$2")
  [ -n "$3" ] && at=" ${gray}↻$(left "$3")"
  printf '%s' "${gray}$1 $(level "$n")${n}%${at}${reset}"
}

top=""
if [ -n "$cwd" ]; then
  dir=$cwd
  if [ -n "$HOME" ] && { [ "$dir" = "$HOME" ] || [[ $dir == "$HOME"/* ]]; }; then
    dir="~${dir#"$HOME"}"
  fi
  top="${blue}${dir}${reset}"
  branch=$(git --no-optional-locks -C "$cwd" symbolic-ref --quiet --short HEAD 2>/dev/null ||
    git --no-optional-locks -C "$cwd" rev-parse --short HEAD 2>/dev/null)
  [ -n "$branch" ] && top+=" ${cyan}(${branch})${reset}"
fi
who=""
[ -n "$model" ] && who="${purple}${model}${reset}"
[ -n "$effort" ] && who+=" ${gray}• ${pink}${effort}${reset}"
top=$(join "$top" "$who")

bottom=""
if [ -n "$ctx" ]; then
  n=$(LC_ALL=C printf '%.0f' "$ctx")
  bottom="${gray}ctx $(level $((100 - n)))${n}%${reset}"
fi
bottom=$(join "$bottom" "$(usage 5h "$five" "$five_at")")
bottom=$(join "$bottom" "$(usage 7d "$week" "$week_at")")

printf '%s\n' "$top"
if [ -n "$bottom" ]; then printf '%s\n' "$bottom"; fi
