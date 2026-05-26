#!/usr/bin/env bash
# eagleeye-web — start the dashboard server and open the browser
JAVA="$(command -v java)"
JAR="/opt/eagleeye/web/eagleeye-web.jar"
PORT=8080
URL="http://localhost:$PORT/dashboard"

exec "$JAVA" \
  --enable-native-access=ALL-UNNAMED \
  -Dspring.profiles.active=prod \
  -Dlogging.level.root=WARN \
  -Dlogging.level.com.eagleeye=INFO \
  -Dlogging.level.org.springframework=WARN \
  -jar "$JAR" &

echo "Starting EagleEye dashboard..."
sleep 3
open "$URL"
wait
