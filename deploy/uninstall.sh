#!/usr/bin/env bash
# EagleEye macOS uninstaller
set -euo pipefail

PLIST_DST="$HOME/Library/LaunchAgents/com.eagleeye.collector.plist"

echo "==> Stopping and removing launchd agent..."
launchctl bootout "gui/$(id -u)/com.eagleeye.collector" 2>/dev/null || true
rm -f "$PLIST_DST"

echo "==> Removing binaries..."
sudo rm -rf /opt/eagleeye
sudo rm -f /usr/local/bin/eagleeye

echo ""
echo "Uninstalled. Data at ~/.eagleeye is preserved."
echo "To also remove data: rm -rf ~/.eagleeye"
