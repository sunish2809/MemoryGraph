#!/bin/sh
set -eu

# Railway volumes are often root-owned. The JVM runs as `app` and must be able to write photos.
mkdir -p /var/lib/memorygraph/storage
if [ "$(id -u)" = "0" ]; then
  chown -R app:app /var/lib/memorygraph/storage || true
  exec su-exec app java -XX:MaxRAMPercentage=75 -jar application.jar
fi

exec java -XX:MaxRAMPercentage=75 -jar application.jar
