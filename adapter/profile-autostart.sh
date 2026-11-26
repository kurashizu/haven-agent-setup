
# auto-start agent (disable: touch ~/.no-autoclaude)
case $- in *i*)
  if [ -t 0 ] && [ -t 1 ] && [ -z "$CLAUDECODE" ] && [ ! -e "$HOME/.no-autoclaude" ] && command -v claude >/dev/null; then  # the agent command
    claude
  fi ;;
esac
