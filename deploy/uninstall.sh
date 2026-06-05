#!/usr/bin/env bash
# EagleEye macOS uninstaller
set -euo pipefail

LAUNCH_AGENTS="$HOME/Library/LaunchAgents"
COLLECTORS=(futah taiex iflow taifex margin)

echo "==> Stopping and removing launchd agents..."
# Per-collector jobs (current layout)
for c in "${COLLECTORS[@]}"; do
    label="com.eagleeye.collector.$c"
    launchctl bootout "gui/$(id -u)/$label" 2>/dev/null || true
    rm -f "$LAUNCH_AGENTS/$label.plist"
done
# Legacy single job (pre-refactor), if still present
launchctl bootout "gui/$(id -u)/com.eagleeye.collector" 2>/dev/null || true
rm -f "$LAUNCH_AGENTS/com.eagleeye.collector.plist"

# Web dashboard
launchctl bootout "gui/$(id -u)/com.eagleeye.web" 2>/dev/null || true
rm -f "$LAUNCH_AGENTS/com.eagleeye.web.plist"

echo "==> Removing binaries..."
sudo rm -rf /opt/eagleeye
sudo rm -f /usr/local/bin/eagleeye /usr/local/bin/eagleeye-backfill /usr/local/bin/eagleeye-web

echo ""
echo "Uninstalled. Data at ~/.eagleeye is preserved."
echo "To also remove data: rm -rf ~/.eagleeye"
