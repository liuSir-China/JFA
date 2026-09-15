#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
if [[ -z "${JAVA_HOME:-}" ]]; then
  JAVA_BIN="$(command -v java || true)"
else
  JAVA_BIN="$JAVA_HOME/bin/java"
fi
if [[ -z "${JAVA_BIN}" || ! -x "${JAVA_BIN}" ]]; then
  echo "ERROR: java not found. Install JDK 8 and set JAVA_HOME." >&2
  exit 99
fi
exec "$JAVA_BIN" -Dfile.encoding=UTF-8 -jar "$ROOT/lib/jfa.jar" --config "$ROOT/conf/jfa.properties" "$@"
