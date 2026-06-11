#!/usr/bin/env bash
set -euo pipefail

AGENT_DIR="$(dirname "$0")/../backend/otel"
AGENT_JAR="$AGENT_DIR/opentelemetry-javaagent.jar"
AGENT_VERSION="${OTEL_VERSION:-latest}"

if [ -f "$AGENT_JAR" ]; then
    echo "OTEL agent already exists at $AGENT_JAR"
    exit 0
fi

mkdir -p "$AGENT_DIR"

if [ "$AGENT_VERSION" = "latest" ]; then
    echo "Downloading latest OpenTelemetry Java agent..."
    curl -sL -o "$AGENT_JAR" \
        https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar
else
    echo "Downloading OpenTelemetry Java agent v$AGENT_VERSION..."
    curl -sL -o "$AGENT_JAR" \
        "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v$AGENT_VERSION/opentelemetry-javaagent.jar"
fi

echo "Downloaded: $AGENT_JAR ($(du -h "$AGENT_JAR" | cut -f1))"
