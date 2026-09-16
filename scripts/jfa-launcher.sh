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

console_port() {
  local p=""
  if [[ -f "$ROOT/conf/jfa.properties" ]]; then
    p="$(grep -E '^console.port=' "$ROOT/conf/jfa.properties" | tail -n 1 | cut -d= -f2- | tr -d '[:space:]' || true)"
  fi
  echo "${p:-8080}"
}

console_host() {
  local ip=""
  ip="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
  if [[ -z "${ip}" ]]; then
    ip="127.0.0.1"
  fi
  echo "$ip"
}

start_console_bg() {
  mkdir -p "$ROOT/run"
  local pidfile="$ROOT/run/jfa-console.pid"
  local logfile="$ROOT/run/jfa-console.log"
  if [[ -f "$pidfile" ]]; then
    local old
    old="$(tr -d '[:space:]' < "$pidfile" || true)"
    if [[ -n "${old}" ]] && kill -0 "$old" 2>/dev/null; then
      echo "JFA web console already running (pid $old)"
      echo "Browse http://$(console_host):$(console_port)/jfa"
      exit 0
    fi
    rm -f "$pidfile"
  fi
  nohup env -u JFA_CMD "$JAVA_BIN" -Dfile.encoding=UTF-8 -jar "$ROOT/lib/jfa.jar" --config "$ROOT/conf/jfa.properties" start "$@" >>"$logfile" 2>&1 &
  local pid=$!
  echo "$pid" > "$pidfile"
  sleep 0.4
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "ERROR: JFA web console failed to start. See $logfile" >&2
    rm -f "$pidfile"
    exit 1
  fi
  echo "JFA web console started (pid $pid)"
  echo "Browse http://$(console_host):$(console_port)/jfa"
  exit 0
}

CMD="${JFA_CMD:-}"
PREFIX=()
case "$CMD" in
  jfa)
    if [[ $# -eq 0 ]]; then
      PREFIX=(discover)
    elif [[ "$1" == "start" ]]; then
      shift
      start_console_bg "$@"
    elif [[ "$1" == "stop" ]]; then
      PREFIX=(stop)
      shift
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
