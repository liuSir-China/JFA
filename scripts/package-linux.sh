#!/usr/bin/env bash
# Build the JDK 8 Linux CLI layout:
#   dist/jfa-linux/bin/jfa
#   dist/jfa-linux/bin/jfa-analyze
#   dist/jfa-linux/bin/jfa-file-analyze
#   dist/jfa-linux/bin/jfa-collect
#   dist/jfa-linux/bin/jfa-config
#   dist/jfa-linux/lib/jfa.jar
#   dist/jfa-linux/conf/jfa.properties
#   dist/jfa-linux/run/          (jfa-console.pid)
#   dist/jfa-linux/docs/
#   dist/jfa-linux/reportfile/
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -x "$HOME/opt/jdk8/bin/java" ]]; then
    export JAVA_HOME="$HOME/opt/jdk8"
  fi
fi
export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}${PATH:-}"

JAVA_VER="$(java -version 2>&1 | head -n 1 || true)"
echo "Using JAVA_HOME=${JAVA_HOME:-} ($JAVA_VER)"

MVN=mvn
if [[ -x "$HOME/opt/maven/bin/mvn" ]]; then
  MVN="$HOME/opt/maven/bin/mvn"
fi

"$MVN" -q -DskipTests=false package

DEST="$ROOT/dist/jfa-linux"
rm -rf "$DEST"
mkdir -p "$DEST/bin" "$DEST/lib" "$DEST/conf" "$DEST/docs" "$DEST/testdata" "$DEST/reportfile" "$DEST/run"

cp -f "$ROOT/jfa-cli/target/jfa-cli-1.0.0.jar" "$DEST/lib/jfa.jar"
cp -f "$ROOT/conf/jfa.properties" "$DEST/conf/jfa.properties"
cp -f "$ROOT/README.md" "$DEST/README.md"
cp -f "$ROOT/docs/quickstart.md" "$DEST/docs/quickstart.md"
if [[ -f "$ROOT/docs/user-manual.md" ]]; then
  cp -f "$ROOT/docs/user-manual.md" "$DEST/docs/user-manual.md"
fi
cp -R "$ROOT/testdata/." "$DEST/testdata/"

# Copy launchers with Unix LF only (CRLF makes bash fail with $'\r').
copy_lf() {
  local src="$1"
  local dst="$2"
  tr -d '\r' < "$src" > "$dst"
  chmod +x "$dst"
}

copy_lf "$ROOT/scripts/jfa-launcher.sh" "$DEST/bin/jfa-launcher.sh"
copy_lf "$ROOT/scripts/jfa" "$DEST/bin/jfa"
copy_lf "$ROOT/scripts/jfa-analyze" "$DEST/bin/jfa-analyze"
copy_lf "$ROOT/scripts/jfa-file-analyze" "$DEST/bin/jfa-file-analyze"
copy_lf "$ROOT/scripts/jfa-collect" "$DEST/bin/jfa-collect"
copy_lf "$ROOT/scripts/jfa-config" "$DEST/bin/jfa-config"

echo "Packaged: $DEST"
echo "Try: $DEST/bin/jfa --help"
echo "     $DEST/bin/jfa-analyze"
echo "     $DEST/bin/jfa-file-analyze"
echo "     $DEST/bin/jfa-collect"
echo "     $DEST/bin/jfa start"
echo "     $DEST/bin/jfa stop"
