#!/usr/bin/env bash
# One-shot backfill runner
# Usage: eagleeye-backfill --from 2026-01-01 --to 2026-03-15

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

echo "Backfilling $FROM → $TO ..."
exec java -jar /opt/eagleeye/collector/eagleeye-collector.jar \
     --spring.profiles.active=prod \
     --backfill.from="$FROM" \
     --backfill.to="$TO" \
     --logging.level.root=WARN \
     --logging.level.com.eagleeye=INFO
