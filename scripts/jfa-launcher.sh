#!/usr/bin/env bash
# Shared launcher for packaged bin/* commands.
# Wrappers set JFA_CMD then exec this script.
# Unix LF only (CRLF makes bash fail with $'\r').
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
mkdir -p "$ROOT/reportfile"
if [[ -z "${JAVA_HOME:-}" ]]; then
  JAVA_BIN="$(command -v java || true)"
else
  JAVA_BIN="$JAVA_HOME/bin/java"
fi
if [[ -z "${JAVA_BIN}" || ! -x "${JAVA_BIN}" ]]; then
  echo "ERROR: java not found. Install JDK 8 and set JAVA_HOME." >&2
  exit 99
fi

CMD="${JFA_CMD:-}"
PREFIX=()
case "$CMD" in
  jfa)
    if [[ $# -eq 0 ]]; then
      PREFIX=(discover)
    elif [[ "$1" == "--help" || "$1" == "help" || "$1" == "-h" ]]; then
      PREFIX=(help config)
      shift
    else
      PREFIX=(discover)
    fi
    ;;
  jfa-analyze)
    PREFIX=(diagnose)
    ;;
  jfa-file-analyze)
    PREFIX=(analyze)
    ;;
  jfa-collect)
    PREFIX=(collect)
    ;;
  jfa-config)
    PREFIX=(help config)
    ;;
  *)
    PREFIX=()
    ;;
esac

if [[ ${#PREFIX[@]} -gt 0 ]]; then
  exec "$JAVA_BIN" -Dfile.encoding=UTF-8 -jar "$ROOT/lib/jfa.jar" --config "$ROOT/conf/jfa.properties" "${PREFIX[@]}" "$@"
else
  exec "$JAVA_BIN" -Dfile.encoding=UTF-8 -jar "$ROOT/lib/jfa.jar" --config "$ROOT/conf/jfa.properties" "$@"
fi
