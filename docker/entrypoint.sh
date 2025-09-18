#!/bin/sh
set -e

# Start haveged if available
if command -v haveged >/dev/null 2>&1; then
  haveged -F >/dev/null 2>&1 &
fi

# Default CONFIG_PATH
export CONFIG_PATH=${CONFIG_PATH:-/app/config.yml}

exec java -jar /app/app.jar
#!/bin/sh
set -e

# Start haveged in foreground if available in background
if command -v haveged >/dev/null 2>&1; then
  echo "Starting haveged for entropy"
  haveged -F >/dev/null 2>&1 &
fi

# Ensure config path points to a file if present
if [ -n "${CONFIG_PATH}" ] && [ -f "${CONFIG_PATH}" ]; then
  echo "Using CONFIG_PATH=${CONFIG_PATH}"
fi

echo "Exec java -jar /app/app.jar"
exec java -jar /app/app.jar
