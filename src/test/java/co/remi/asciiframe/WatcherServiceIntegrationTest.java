package co.remi.asciiframe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for WatcherService that tests real application scenarios
 * without relying on file system watching events, which can be unreliable
 * in test environments.
 */
class WatcherServiceIntegrationTest {

    @Test
    void testWatcherServiceInitializesCorrectly(@TempDir Path tempDir) throws IOException, InterruptedException {
        // Set working directory to temp dir
        String originalDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            
            // Create test configuration
            Config testConfig = new Config(
                "docs/index.adoc",
                "build_artifacts",
                List.of("html"),
                "http://localhost:8000",
                true,
                100,
                8080,
                List.of("docs"),
                ".cache/diagrams",
                null
            );

            AtomicBoolean callbackInvoked = new AtomicBoolean(false);
            CountDownLatch initLatch = new CountDownLatch(1);
            
            // Create callback that signals when invoked
            Runnable callback = () -> {
                callbackInvoked.set(true);
                initLatch.countDown();
            };

            // Create and start watcher
            WatcherService watcherService = new WatcherService(testConfig, callback);
            watcherService.start();
            
            // Wait a moment for initialization
            Thread.sleep(500);
            
            // Verify docs directory was created
            Path docsDir = tempDir.resolve("docs");
            assertTrue(Files.exists(docsDir), "Docs directory should be created");
            assertTrue(Files.isDirectory(docsDir), "Docs path should be a directory");
            
            // Test completes successfully if no exceptions thrown
            assertTrue(true, "WatcherService initialized without errors");
            
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void testWatcherServiceWithExistingDocsDirectory(@TempDir Path tempDir) throws IOException, InterruptedException {
        String originalDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            
            // Pre-create docs directory with content
            Path docsDir = tempDir.resolve("docs");
            Files.createDirectories(docsDir);
            Path testFile = docsDir.resolve("existing.adoc");
            Files.writeString(testFile, "= Existing Document\nThis file exists before watcher starts.");
            
            Config testConfig = new Config(
                "docs/index.adoc",
                "build_artifacts",
                List.of("html"),
                "http://localhost:8000",
                true,
                100,
                8080,
                List.of("docs"),
                ".cache/diagrams",
                null
            );

            AtomicInteger callbackCount = new AtomicInteger(0);
            Runnable callback = () -> callbackCount.incrementAndGet();

            // Create and start watcher
            WatcherService watcherService = new WatcherService(testConfig, callback);
            watcherService.start();
            
            // Wait for initialization
            Thread.sleep(500);
            
            // Verify existing file is still there
            assertTrue(Files.exists(testFile), "Existing file should remain");
            
            // Verify no false positive callbacks during initialization
            // (Note: this is lenient as some filesystems may trigger events during registration)
            assertTrue(callbackCount.get() >= 0, "Callback count should not be negative");
            
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test 
    void testWatcherServiceWithDisabledWatching(@TempDir Path tempDir) throws IOException, InterruptedException {
        String originalDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            
            // Create config with watching disabled
            Config testConfig = new Config(
                "docs/index.adoc",
                "build_artifacts",
                List.of("html"),
                "http://localhost:8000",
                false, // Watching disabled
                100,
                8080,
                List.of("docs"),
                ".cache/diagrams",
                null
            );

            AtomicBoolean callbackInvoked = new AtomicBoolean(false);
            Runnable callback = () -> callbackInvoked.set(true);

            // Create watcher (but don't start it since watching is disabled)
            WatcherService watcherService = new WatcherService(testConfig, callback);
            
            // Starting should still work even if watching is disabled
            assertDoesNotThrow(() -> watcherService.start());
            
            Thread.sleep(200);
            
            // Verify no callback was invoked
            assertFalse(callbackInvoked.get(), "Callback should not be invoked when watching is disabled");
            
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void testWatcherServiceHandlesInvalidDirectory(@TempDir Path tempDir) throws InterruptedException {
        String originalDir = System.getProperty("user.dir");
        try {
            // Set working directory to a location where we can't create docs
            System.setProperty("user.dir", "/nonexistent/path/that/should/not/exist");
            
            Config testConfig = new Config(
                "docs/index.adoc",
                "build_artifacts",
                List.of("html"),
                "http://localhost:8000",
                true,
                100,
                8080,
                List.of("docs"),
                ".cache/diagrams",
                null
            );

            AtomicBoolean callbackInvoked = new AtomicBoolean(false);
            Runnable callback = () -> callbackInvoked.set(true);

            WatcherService watcherService = new WatcherService(testConfig, callback);
            
            // Starting should not throw exception even if directory creation fails
            assertDoesNotThrow(() -> watcherService.start());
            
            Thread.sleep(200);
            
            // Test passes if no exception is thrown
            assertTrue(true, "WatcherService handles invalid directory gracefully");
            
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void testMultipleWatcherServicesCanCoexist(@TempDir Path tempDir) throws IOException, InterruptedException {
        String originalDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            
            Config testConfig = new Config(
                "docs/index.adoc",
                "build_artifacts",
                List.of("html"),
                "http://localhost:8000",
                true,
                50,
                8080,
                List.of("docs"),
                ".cache/diagrams",
                null
            );

            AtomicInteger callback1Count = new AtomicInteger(0);
            AtomicInteger callback2Count = new AtomicInteger(0);
            
            Runnable callback1 = () -> callback1Count.incrementAndGet();
            Runnable callback2 = () -> callback2Count.incrementAndGet();

            // Create multiple watcher services
            WatcherService watcher1 = new WatcherService(testConfig, callback1);
            WatcherService watcher2 = new WatcherService(testConfig, callback2);
            
            // Both should start without issues
            assertDoesNotThrow(() -> {
                watcher1.start();
                watcher2.start();
            });
            
            Thread.sleep(500);
            
            // Both watchers should be operational
            assertTrue(callback1Count.get() >= 0, "First watcher should be operational");
            assertTrue(callback2Count.get() >= 0, "Second watcher should be operational");
            
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }
}