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
DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
LAUNCH_AGENTS="$HOME/Library/LaunchAgents"
BIN_LINK="/usr/local/bin/eagleeye"
BACKFILL_LINK="/usr/local/bin/eagleeye-backfill"
WEB_LINK="/usr/local/bin/eagleeye-web"

# Collectors, each its own launchd job (name-addressed via --collector=NAME).
COLLECTORS=(futah taiex iflow taifex mktoi margin txtick)

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
sudo cp "$DEPLOY_DIR/eagleeye-backfill.sh" "$BACKFILL_LINK"
sudo chmod +x "$BACKFILL_LINK"

# ── 5c. Web wrapper ───────────────────────────────────────────────────────────
echo "==> Installing web wrapper..."
sudo cp "$DEPLOY_DIR/eagleeye-web.sh" "$WEB_LINK"
sudo chmod +x "$WEB_LINK"

# ── 5. Detect Java home for plist ─────────────────────────────────────────────
# Use the java on PATH (handles SDKMAN, Homebrew, etc.) rather than
# /usr/libexec/java_home which only sees JVMs in /Library/Java.
JAVA_BIN="$(command -v java)"
JAVA_HOME_VAL="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"

# ── 5d. Pre-generate CDS archive ──────────────────────────────────────────────
# Warm the Class Data Sharing archive now (clean onRefresh exit) so the first
# scheduled collection is already fast, rather than paying the one-time create
# cost on a real run. Same flags as the plist so the archive matches at runtime.
echo "==> Pre-generating collector CDS archive..."
"$JAVA_BIN" --enable-native-access=ALL-UNNAMED \
  -XX:+AutoCreateSharedArchive -XX:SharedArchiveFile="$COLLECTOR_DIR/collector.jsa" \
  -Xlog:cds=off -Dspring.context.exit=onRefresh \
  -jar "$COLLECTOR_DIR/eagleeye-collector.jar" \
  --spring.profiles.active=prod --logging.level.root=WARN >/dev/null 2>&1 \
  && echo "    archive: $COLLECTOR_DIR/collector.jsa" \
  || echo "    (skipped — archive will be created on first scheduled run)"

# ── 6. Install launchd agents (one per collector) ─────────────────────────────
# Each collector is its own job: it owns its schedule and names which collector
# to run via --collector=NAME. Adding a data source = a new plist here, nothing
# else to change.
echo "==> Installing launchd agents..."
mkdir -p "$LAUNCH_AGENTS"

for c in "${COLLECTORS[@]}"; do
    label="com.eagleeye.collector.$c"
    src="$DEPLOY_DIR/$label.plist"
    dst="$LAUNCH_AGENTS/$label.plist"

    if [[ ! -f "$src" ]]; then
        echo "    WARN: missing $src — skipping" >&2
        continue
    fi

    sed "s|/usr/local/opt/openjdk@25|$JAVA_HOME_VAL|g" "$src" > "$dst"

    launchctl bootout "gui/$(id -u)/$label" 2>/dev/null || true
    launchctl bootstrap "gui/$(id -u)" "$dst"
    echo "    loaded $label"
done

# ── 7. Install launchd agent for web dashboard ────────────────────────────────
echo "==> Installing web dashboard launchd agent..."
WEB_LABEL="com.eagleeye.web"
WEB_SRC="$DEPLOY_DIR/$WEB_LABEL.plist"
WEB_DST="$LAUNCH_AGENTS/$WEB_LABEL.plist"

sed "s|/usr/local/opt/openjdk@25|$JAVA_HOME_VAL|g" "$WEB_SRC" > "$WEB_DST"

launchctl bootout "gui/$(id -u)/$WEB_LABEL" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$WEB_DST"
echo "    loaded $WEB_LABEL (will start now and on every login)"

echo ""
echo "Done! EagleEye installed."
echo ""
echo "  Collectors run Mon–Fri (Taipei time), each its own launchd job:"
echo "    07:00  FUTAH   after-hours futures (夜盤)"
echo "    15:05  TAIEX   market index        (TWSE afterTrading FMTQIK)"
echo "    15:15  IFLOW   institutional flow  (三大法人)"
echo "    15:30  TAIFEX  TAIFEX OI           (未平倉口數及契約金額)"
echo "    15:35  MKTOI   total market OI     (期貨每日交易行情, MTX/TMF)"
echo "    21:35  MARGIN  margin transactions (融資融券)"
echo "  Manual trigger:  launchctl start com.eagleeye.collector.margin   # one collector"
echo "  Ad-hoc run all:  eagleeye-collector --collector=ALL              # via the jar directly"
echo "  Logs:            tail -f $LOG_DIR/collector-*.log"
echo "  Shell:           eagleeye"
echo "  Backfill all:    eagleeye-backfill --from 2026-01-01 --to 2026-05-28"
echo "  Backfill 夜盤:   eagleeye-backfill --futures-ah --from 2026-01-01 --to 2026-05-28"
echo "  Dashboard:       eagleeye-web"
