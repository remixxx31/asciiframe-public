#!/usr/bin/env bash
set -euo pipefail

# Manual test script for WatcherService file watching functionality
# This script sets up a test environment and provides instructions for manual testing

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

log() { printf "[%s] %s\n" "$(date +%H:%M:%S)" "$*"; }
die() { printf "ERROR: %s\n" "$*" >&2; exit 1; }

TEMP_DIR="/tmp/asciiframe-watch-test-$$"
TEST_DOCS_DIR="$TEMP_DIR/docs"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

usage() {
  cat <<EOF
Manual Test Script for AsciiFrame File Watching

This script helps verify that the WatcherService correctly detects file changes
and triggers callbacks as expected.

Usage: $0 [OPTIONS]

Options:
  -h, --help     Show this help message
  -c, --cleanup  Clean up test files and exit

The script will:
1. Create a temporary test environment
2. Start the AsciiFrame application
3. Provide instructions for manual file modification testing
4. Monitor and report on file change events

EOF
}

cleanup() {
  log "Cleaning up test environment..."
  if [ -d "$TEMP_DIR" ]; then
    rm -rf "$TEMP_DIR"
    log "Removed test directory: $TEMP_DIR"
  fi
  if [ -n "${APP_PID:-}" ] && kill -0 "$APP_PID" 2>/dev/null; then
    log "Stopping application (PID: $APP_PID)..."
    kill "$APP_PID" || true
    sleep 2
    kill -9 "$APP_PID" 2>/dev/null || true
  fi
}

setup_test_environment() {
  log "Setting up test environment in: $TEMP_DIR"
  
  # Create test directory structure
  mkdir -p "$TEST_DOCS_DIR"
  
  # Create initial test document
  cat > "$TEST_DOCS_DIR/test-document.adoc" <<EOF
= File Watch Test Document
Test Author
:toc:

== Introduction

This document is used to test the file watching functionality of AsciiFrame.
The watcher should detect when this file is modified.

== Test Instructions

1. Modify this file
2. Save the changes
3. Observe if the application detects the change

Current timestamp: $(date)

== Test Content

This is the initial content. Modify this section to trigger watch events.
EOF

  # Create a simple index document
  cat > "$TEST_DOCS_DIR/index.adoc" <<EOF
= AsciiFrame Watch Test
Test Author

This is the main test document for verifying file watching functionality.

include::test-document.adoc[]

== Live Testing

Modify the included document or this document to test file watching.
EOF

  log "Created test documents in: $TEST_DOCS_DIR"
}

start_application() {
  log "Building application..."
  ./gradlew -q shadowJar || die "Failed to build application"
  
  log "Starting application in test directory..."
  cd "$TEMP_DIR"
  
  # Create a test config that enables watching
  cat > config.yml <<EOF
entry: docs/index.adoc
outDir: build_artifacts
formats: [html, pdf]

theme:
  html: default
  pdf: default

diagrams:
  engine: kroki
  url: http://localhost:8000

watch:
  enabled: true
  debounceMs: 250

server:
  port: 8080
EOF

  # Start the application in background
  java -jar "$ROOT_DIR/build/libs/app-fat.jar" > application.log 2>&1 &
  APP_PID=$!
  
  log "Application started with PID: $APP_PID"
  log "Waiting for application to initialize..."
  
  # Wait for application to start
  for i in {1..30}; do
    if curl -s http://localhost:8080/health >/dev/null 2>&1; then
      log "Application is ready!"
      break
    fi
    if ! kill -0 "$APP_PID" 2>/dev/null; then
      die "Application failed to start. Check application.log"
    fi
    sleep 1
  done
  
  if ! curl -s http://localhost:8080/health >/dev/null 2>&1; then
    log "Application logs:"
    cat application.log
    die "Application did not become ready within 30 seconds"
  fi
}

monitor_logs() {
  log "Monitoring application logs for file change events..."
  echo -e "${YELLOW}Look for messages indicating file changes detected${NC}"
  
  # Monitor logs in background
  tail -f application.log | grep -E "(build:changed|onChange|File.*changed|docs.*modified)" --line-buffered | while read -r line; do
    echo -e "${GREEN}[WATCH EVENT] $line${NC}"
  done &
  TAIL_PID=$!
}

run_manual_tests() {
  echo -e "\n${BLUE}=== Manual File Watching Tests ===${NC}"
  echo -e "Test environment ready! The application is watching: ${YELLOW}$TEST_DOCS_DIR${NC}"
  echo -e "Application logs: ${YELLOW}$TEMP_DIR/application.log${NC}"
  echo
  echo -e "${YELLOW}Test Instructions:${NC}"
  echo "1. Open $TEST_DOCS_DIR/test-document.adoc in your editor"
  echo "2. Make changes to the file and save"
  echo "3. Observe the console output for file change events"
  echo "4. Try creating new .adoc files in the docs directory"
  echo "5. Try deleting files to test deletion events"
  echo
  echo -e "${YELLOW}Expected behavior:${NC}"
  echo "- File modifications should trigger watch events"
  echo "- Events should be debounced (rapid changes = fewer events)"
  echo "- WebSocket notifications should be sent to connected clients"
  echo
  echo -e "${YELLOW}Additional tests:${NC}"
  echo "- Visit http://localhost:8080/preview/index.html to see generated content"
  echo "- Use WebSocket to connect to ws://localhost:8080/events for live updates"
  echo
  echo -e "${RED}Press Ctrl+C to stop the test and cleanup${NC}"
  
  # Keep script running until interrupted
  trap cleanup EXIT INT TERM
  
  # Monitor logs and wait
  monitor_logs
  
  while true; do
    sleep 1
  done
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    -h|--help)
      usage
      exit 0
      ;;
    -c|--cleanup)
      cleanup
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      usage
      exit 1
      ;;
  esac
done

# Main execution
log "Starting AsciiFrame File Watch Manual Test"

trap cleanup EXIT INT TERM

setup_test_environment
start_application
run_manual_tests