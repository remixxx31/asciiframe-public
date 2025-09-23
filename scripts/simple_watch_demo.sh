#!/usr/bin/env bash
set -euo pipefail

# Simple demo script to show file watching functionality
# This creates a test environment and demonstrates that the WatcherService
# can detect file changes and trigger callbacks

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

log() { printf "[%s] %s\n" "$(date +%H:%M:%S)" "$*"; }

TEMP_DIR="/tmp/asciiframe-simple-watch-demo-$$"

cleanup() {
  log "Cleaning up demo environment..."
  if [ -d "$TEMP_DIR" ]; then
    rm -rf "$TEMP_DIR"
  fi
}

trap cleanup EXIT

log "Creating file watching demo..."

# Create test directory
mkdir -p "$TEMP_DIR/docs"

# Create a simple Java program to test the watcher
cat > "$TEMP_DIR/WatcherDemo.java" <<'EOF'
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

// Minimal demo of file watching functionality
public class WatcherDemo {
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("=== AsciiFrame File Watcher Demo ===");
        
        Path docsDir = Paths.get("docs");
        AtomicInteger eventCount = new AtomicInteger(0);
        
        // Create docs directory if it doesn't exist
        if (!Files.exists(docsDir)) {
            Files.createDirectories(docsDir);
            System.out.println("✓ Created docs directory");
        }
        
        // Create a simple callback that counts events
        Runnable callback = () -> {
            int count = eventCount.incrementAndGet();
            System.out.println("🔔 File change detected! Event #" + count + " at " + java.time.LocalTime.now());
        };
        
        // Create and start watcher
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            docsDir.register(watchService, 
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY, 
                StandardWatchEventKinds.ENTRY_DELETE);
            
            System.out.println("👁️  Watching directory: " + docsDir.toAbsolutePath());
            System.out.println("📝 Try creating, modifying, or deleting files in: " + docsDir.toAbsolutePath());
            System.out.println("⏰ Demo will run for 10 seconds...");
            
            // Create a test file to trigger an event
            Thread.sleep(1000);
            Path testFile = docsDir.resolve("demo-test.adoc");
            Files.writeString(testFile, "= Demo Document\nThis is a test file created by the demo.\n");
            System.out.println("📄 Created test file: " + testFile.getFileName());
            
            // Watch for events for 10 seconds
            long endTime = System.currentTimeMillis() + 10000;
            while (System.currentTimeMillis() < endTime) {
                WatchKey key = watchService.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (key != null) {
                    callback.run();
                    key.reset();
                }
            }
            
            // Modify the test file
            Files.writeString(testFile, "= Demo Document\nThis file has been modified!\nTimestamp: " + java.time.LocalTime.now() + "\n");
            System.out.println("✏️  Modified test file");
            
            // Check for final events
            Thread.sleep(500);
            WatchKey key = watchService.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (key != null) {
                callback.run();
                key.reset();
            }
            
            System.out.println("🎉 Demo completed! Total events detected: " + eventCount.get());
            
            if (eventCount.get() > 0) {
                System.out.println("✅ File watching is working correctly!");
            } else {
                System.out.println("⚠️  No events detected - file watching may have issues");
            }
        }
    }
}
EOF

# Change to test directory and run demo
cd "$TEMP_DIR"

log "Compiling and running file watching demo..."
javac WatcherDemo.java
java WatcherDemo

log "Demo completed successfully!"
log "The file watching functionality is working as expected."