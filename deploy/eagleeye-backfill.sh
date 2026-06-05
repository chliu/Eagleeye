#!/usr/bin/env bash
# One-shot backfill runner
# Usage:
#   eagleeye-backfill --from 2026-01-01 [--to 2026-05-28]              # combined (all regular collectors)
#   eagleeye-backfill --futures-ah --from 2026-01-01 [--to 2026-05-28] # after-hours futures only

set -euo pipefail

FROM=""
TO=""
FUTURES_AH=false
TX_TICK=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --futures-ah) FUTURES_AH=true; shift ;;
        --tx-tick) TX_TICK=true; shift ;;
        --from) FROM="$2"; shift 2 ;;
        --to)   TO="$2";   shift 2 ;;
        *) echo "Unknown argument: $1"; exit 1 ;;
    esac
done

if [[ -z "$FROM" ]]; then
    echo "Usage: eagleeye-backfill [--futures-ah|--tx-tick] --from YYYY-MM-DD [--to YYYY-MM-DD]"
    exit 1
fi

TO="${TO:-$(date +%Y-%m-%d)}"

JAVA="java --enable-native-access=ALL-UNNAMED"
JAR="/opt/eagleeye/collector/eagleeye-collector.jar"
COMMON_ARGS="--spring.profiles.active=prod --logging.level.root=WARN --logging.level.com.eagleeye=INFO"

if [[ "$FUTURES_AH" == true ]]; then
    echo "=== After-hours futures backfill: $FROM → $TO ==="
    $JAVA -jar "$JAR" $COMMON_ARGS \
        --futures-ah.backfill.from="$FROM" \
        --futures-ah.backfill.to="$TO"
elif [[ "$TX_TICK" == true ]]; then
    echo "=== TX Tick backfill: $FROM → $TO ==="
    $JAVA -jar "$JAR" $COMMON_ARGS \
        --txtick.backfill.from="$FROM" \
        --txtick.backfill.to="$TO"
else
    echo "=== Backfilling $FROM → $TO ==="
    $JAVA -jar "$JAR" $COMMON_ARGS \
        --combined.backfill.from="$FROM" \
        --combined.backfill.to="$TO"
fi
