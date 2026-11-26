
# auto-start claude (disable: touch ~/.no-autoclaude)
case $- in *i*)
  if [ -t 0 ] && [ -t 1 ] && [ -z "$CLAUDECODE" ] && [ ! -e "$HOME/.no-autoclaude" ] && command -v claude >/dev/null; then
    claude
  fi ;;
esac
