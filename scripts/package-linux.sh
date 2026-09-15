#!/usr/bin/env bash
# Build the JDK 8 Linux CLI layout:
#   dist/jfa-linux/bin/jfa
#   dist/jfa-linux/lib/jfa.jar
#   dist/jfa-linux/conf/jfa.properties
#   dist/jfa-linux/docs/
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
mkdir -p "$DEST/bin" "$DEST/lib" "$DEST/conf" "$DEST/docs" "$DEST/testdata"

cp -f "$ROOT/jfa-cli/target/jfa-cli-1.0.0.jar" "$DEST/lib/jfa.jar"
cp -f "$ROOT/conf/jfa.properties" "$DEST/conf/jfa.properties"
cp -f "$ROOT/README.md" "$DEST/README.md"
cp -f "$ROOT/docs/quickstart.md" "$DEST/docs/quickstart.md"
if [[ -f "$ROOT/docs/user-manual.md" ]]; then
  cp -f "$ROOT/docs/user-manual.md" "$DEST/docs/user-manual.md"
fi
cp -R "$ROOT/testdata/." "$DEST/testdata/"

cat > "$DEST/bin/jfa" << 'LAUNCH'
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
LAUNCH
chmod +x "$DEST/bin/jfa"

echo "Packaged: $DEST"
echo "Try: $DEST/bin/jfa help"
