#!/usr/bin/env bash
# One-shot backfill runner — collects TAIEX market index + TAIFEX institutional data
# Usage: eagleeye-backfill --from 2026-01-01 [--to 2026-03-18]

set -euo pipefail

FROM=""
TO=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --from) FROM="$2"; shift 2 ;;
        --to)   TO="$2";   shift 2 ;;
        *) echo "Unknown argument: $1"; exit 1 ;;
    esac
done

if [[ -z "$FROM" ]]; then
    echo "Usage: eagleeye-backfill --from YYYY-MM-DD [--to YYYY-MM-DD]"
    exit 1
fi

TO="${TO:-$(date +%Y-%m-%d)}"

JAVA="java --enable-native-access=ALL-UNNAMED"
JAR="/opt/eagleeye/collector/eagleeye-collector.jar"
COMMON_ARGS="--spring.profiles.active=prod --logging.level.root=WARN --logging.level.com.eagleeye=INFO"

echo "=== Backfilling $FROM → $TO ==="
$JAVA -jar "$JAR" $COMMON_ARGS \
    --combined.backfill.from="$FROM" \
    --combined.backfill.to="$TO"
