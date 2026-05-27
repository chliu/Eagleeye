#!/usr/bin/env bash
# EagleEye macOS installer
# Usage: ./deploy/install.sh

set -euo pipefail

INSTALL_DIR="/opt/eagleeye"
COLLECTOR_DIR="$INSTALL_DIR/collector"
SHELL_DIR="$INSTALL_DIR/shell"
WEB_DIR="$INSTALL_DIR/web"
LOG_DIR="$INSTALL_DIR/logs"
DATA_DIR="$HOME/.eagleeye/data"
PLIST_SRC="$(dirname "$0")/com.eagleeye.collector.plist"
PLIST_DST="$HOME/Library/LaunchAgents/com.eagleeye.collector.plist"
BIN_LINK="/usr/local/bin/eagleeye"
BACKFILL_LINK="/usr/local/bin/eagleeye-backfill"
WEB_LINK="/usr/local/bin/eagleeye-web"

# ── 1. Build ──────────────────────────────────────────────────────────────────
echo "==> Building JARs..."
mvn package -pl eagleeye-collector,eagleeye-shell,eagleeye-web -am -DskipTests -q

# ── 2. Create directories ─────────────────────────────────────────────────────
echo "==> Creating directories..."
sudo mkdir -p "$COLLECTOR_DIR" "$SHELL_DIR" "$WEB_DIR" "$LOG_DIR"
sudo chown -R "$(whoami)" "$INSTALL_DIR"
mkdir -p "$DATA_DIR"

# ── 3. Copy JARs ──────────────────────────────────────────────────────────────
echo "==> Installing JARs..."
cp eagleeye-collector/target/eagleeye-collector-*-exec.jar "$COLLECTOR_DIR/eagleeye-collector.jar"
cp eagleeye-shell/target/eagleeye-shell-*.jar            "$SHELL_DIR/eagleeye-shell.jar"
cp eagleeye-web/target/eagleeye-web-*-exec.jar           "$WEB_DIR/eagleeye-web.jar"

# ── 4. Shell wrapper ──────────────────────────────────────────────────────────
echo "==> Installing shell wrapper..."
sudo tee "$BIN_LINK" > /dev/null <<'EOF'
#!/usr/bin/env bash
# eagleeye — interactive or non-interactive depending on whether args are given
#
# Interactive:     eagleeye
# Non-interactive: eagleeye collect --date 2026-05-23
#                  eagleeye "futures list" --date 2026-05-23
JAVA="$(command -v java)"
JAR="/opt/eagleeye/shell/eagleeye-shell.jar"
JVM_FLAGS=(
  --enable-native-access=ALL-UNNAMED
  -Dspring.profiles.active=prod
  -Dlogging.level.root=WARN
  -Dlogging.level.com.eagleeye=WARN
  -Dlogging.level.org.springframework=WARN
)

if [ $# -gt 0 ]; then
  exec "$JAVA" "${JVM_FLAGS[@]}" \
       -Dspring.shell.interactive.enabled=false \
       -Dspring.main.banner-mode=off \
       -Dlogging.level.org.jline=ERROR \
       -jar "$JAR" "$@"
else
  exec "$JAVA" "${JVM_FLAGS[@]}" \
       -jar "$JAR"
fi
EOF
sudo chmod +x "$BIN_LINK"

# ── 5b. Backfill wrapper ──────────────────────────────────────────────────────
echo "==> Installing backfill wrapper..."
sudo cp "$(dirname "$0")/eagleeye-backfill.sh" "$BACKFILL_LINK"
sudo chmod +x "$BACKFILL_LINK"

# ── 5c. Web wrapper ───────────────────────────────────────────────────────────
echo "==> Installing web wrapper..."
sudo cp "$(dirname "$0")/eagleeye-web.sh" "$WEB_LINK"
sudo chmod +x "$WEB_LINK"

# ── 5. Detect Java home for plist ─────────────────────────────────────────────
# Use the java on PATH (handles SDKMAN, Homebrew, etc.) rather than
# /usr/libexec/java_home which only sees JVMs in /Library/Java.
JAVA_BIN="$(command -v java)"
JAVA_HOME_VAL="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"

# ── 6. Install launchd plist ──────────────────────────────────────────────────
echo "==> Installing launchd agent..."
sed "s|/usr/local/opt/openjdk@25|$JAVA_HOME_VAL|g" "$PLIST_SRC" > "$PLIST_DST"

# Reload if already loaded
launchctl bootout "gui/$(id -u)/com.eagleeye.collector" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$PLIST_DST"

echo ""
echo "Done! EagleEye installed."
echo ""
echo "  Collector runs Mon–Fri (Taipei time):"
echo "    13:40  market index        (TWSE close)"
echo "    15:10  institutional flow  (三大法人)"
echo "    15:30  TAIFEX OI           (未平倉口數及契約金額)"
echo "    21:35  margin transactions (融資融券)"
echo "  Manual trigger:  launchctl start com.eagleeye.collector  # runs current time-window collector only"
echo "  Logs:            tail -f $LOG_DIR/collector.log"
echo "  Shell:           eagleeye"
echo "  Backfill:        eagleeye-backfill --from 2026-01-01 --to 2026-03-15
  Dashboard:       eagleeye-web"
