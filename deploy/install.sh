#!/usr/bin/env bash
# EagleEye macOS installer
# Usage: ./deploy/install.sh

set -euo pipefail

INSTALL_DIR="/opt/eagleeye"
COLLECTOR_DIR="$INSTALL_DIR/collector"
SHELL_DIR="$INSTALL_DIR/shell"
LOG_DIR="$INSTALL_DIR/logs"
DATA_DIR="$HOME/.eagleeye/data"
PLIST_SRC="$(dirname "$0")/com.eagleeye.collector.plist"
PLIST_DST="$HOME/Library/LaunchAgents/com.eagleeye.collector.plist"
BIN_LINK="/usr/local/bin/eagleeye"

# ── 1. Build ──────────────────────────────────────────────────────────────────
echo "==> Building JARs..."
mvn package -pl eagleeye-collector,eagleeye-shell -am -DskipTests -q

# ── 2. Create directories ─────────────────────────────────────────────────────
echo "==> Creating directories..."
sudo mkdir -p "$COLLECTOR_DIR" "$SHELL_DIR" "$LOG_DIR"
sudo chown -R "$(whoami)" "$INSTALL_DIR"
mkdir -p "$DATA_DIR"

# ── 3. Copy JARs ──────────────────────────────────────────────────────────────
echo "==> Installing JARs..."
cp eagleeye-collector/target/eagleeye-collector-*-exec.jar "$COLLECTOR_DIR/eagleeye-collector.jar"
cp eagleeye-shell/target/eagleeye-shell-*.jar            "$SHELL_DIR/eagleeye-shell.jar"

# ── 4. Shell wrapper ──────────────────────────────────────────────────────────
echo "==> Installing shell wrapper..."
sudo tee "$BIN_LINK" > /dev/null <<'EOF'
#!/usr/bin/env bash
exec java --enable-native-access=ALL-UNNAMED \
     -jar /opt/eagleeye/shell/eagleeye-shell.jar \
     --spring.profiles.active=prod \
     --logging.level.root=WARN \
     --logging.level.com.eagleeye=WARN \
     --logging.level.org.springframework=WARN \
     "$@"
EOF
sudo chmod +x "$BIN_LINK"

# ── 5. Detect Java home for plist ─────────────────────────────────────────────
JAVA_HOME_VAL="$(/usr/libexec/java_home 2>/dev/null || echo '/usr/local/opt/openjdk')"

# ── 6. Install launchd plist ──────────────────────────────────────────────────
echo "==> Installing launchd agent..."
sed "s|/usr/local/opt/openjdk@25|$JAVA_HOME_VAL|g" "$PLIST_SRC" > "$PLIST_DST"

# Reload if already loaded
launchctl bootout "gui/$(id -u)/com.eagleeye.collector" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$PLIST_DST"

echo ""
echo "Done! EagleEye installed."
echo ""
echo "  Collector runs automatically Mon–Fri at 17:10 Taiwan time."
echo "  Manual trigger:  launchctl start com.eagleeye.collector"
echo "  Logs:            tail -f $LOG_DIR/collector.log"
echo "  Shell:           eagleeye"
echo "  Backfill:        eagleeye-backfill --from 2026-01-01 --to 2026-03-15"
