package co.remi.asciiframe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WatcherServiceDebugTest {

    @Test
    void debugWatcherServiceBehavior(@TempDir Path tempDir) throws IOException, InterruptedException {
        // Reset debug counters
        WatcherService.lastDebugMessage = "";
        WatcherService.pollingStarted = false;
        WatcherService.pollCount = 0;
        WatcherService.changeDetectionCount = 0;
        WatcherService.callbackExecutionCount = 0;
        
        // Set up working directory
        System.setProperty("user.dir", tempDir.toString());
        
        // Create test config
        Config testConfig = new Config(
            "docs/index.adoc",
            "build_artifacts", 
            List.of("html"),
            "http://localhost:8000",
            true, // watchEnabled = true
            50, // short debounce for testing
            8080,
            List.of("docs"),
            ".cache/diagrams",
            null
        );
        
        AtomicInteger callbackCount = new AtomicInteger(0);
        CountDownLatch callbackLatch = new CountDownLatch(1);
        
        Runnable callback = () -> {
            callbackCount.incrementAndGet();
            callbackLatch.countDown();
        };
        
        WatcherService watcherService = new WatcherService(testConfig, callback);
        
        // Start the watcher
        watcherService.start();
        
        // Wait for initialization
        Thread.sleep(500);
        
        // Create docs directory and a test file
        Path docsPath = tempDir.resolve("docs");
        Files.createDirectories(docsPath);
        Path testFile = docsPath.resolve("test.adoc");
        Files.writeString(testFile, "= Test Document\nInitial content");
        
        // Wait a bit more for polling to detect the file
        Thread.sleep(1000);
        
        // Modify the file
        Files.writeString(testFile, "= Test Document\nModified content at " + System.currentTimeMillis());
        
        // Wait for callback
        boolean callbackReceived = callbackLatch.await(2, TimeUnit.SECONDS);
        
        // Gather debug information
        String debugInfo = String.format(
            "Debug Info: lastDebugMessage='%s', pollingStarted=%s, pollCount=%d, changeDetectionCount=%d, callbackExecutionCount=%d, callbackReceived=%s, callbackCount=%d",
            WatcherService.lastDebugMessage,
            WatcherService.pollingStarted,
            WatcherService.pollCount,
            WatcherService.changeDetectionCount,
            WatcherService.callbackExecutionCount,
            callbackReceived,
            callbackCount.get()
        );
        
        // Force failure to show debug info
        fail("Debug info: " + debugInfo);
    }
}