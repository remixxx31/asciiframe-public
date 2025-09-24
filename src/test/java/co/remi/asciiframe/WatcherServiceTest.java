package co.remi.asciiframe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WatcherServiceTest {

    private Config testConfig;
    private WatcherService watcherService;
    private AtomicInteger callbackCount;
    private CountDownLatch callbackLatch;
    private String originalDir;

    @BeforeEach
    void setUp() {
        // Create a test config with short debounce for faster tests
        testConfig = new Config(
            "docs/index.adoc",
            "build_artifacts",
            List.of("html", "pdf"),
            "http://localhost:8000",
            true,
            50, // Short debounce for faster tests
            8080,
            List.of("docs"),
            ".cache/diagrams",
            null
        );

        callbackCount = new AtomicInteger(0);
        callbackLatch = new CountDownLatch(1);
        
        // Create callback that counts invocations
        Runnable callback = () -> {
            callbackCount.incrementAndGet();
            callbackLatch.countDown();
        };

        watcherService = new WatcherService(testConfig, callback);
        
        // Store original directory
        originalDir = System.getProperty("user.dir");
    }

    @AfterEach
    void tearDown() {
        // Restore original directory
        if (originalDir != null) {
            System.setProperty("user.dir", originalDir);
        }
        
        if (watcherService != null) {
            // Note: WatcherService doesn't have a stop method, so we rely on the test ending
        }
    }

    @Test
    void testWatcherServiceCanBeCreated() {
        assertNotNull(watcherService);
        assertEquals(0, callbackCount.get());
    }

    @Test
    void testWatcherServiceCanBeStarted() {
        assertDoesNotThrow(() -> watcherService.start());
    }

    @Test
    void testWatcherServiceStartIsIdempotent() {
        watcherService.start();
        watcherService.start(); // Should not throw or cause issues
        assertDoesNotThrow(() -> watcherService.start());
    }

    @Test
    @Disabled("Temporarily disabled - needs configuration fix for tempDir")
    void testDocsDirectoryIsCreatedIfMissing(@TempDir Path tempDir) throws IOException, InterruptedException {
        // Change to temp directory to test docs creation
        System.setProperty("user.dir", tempDir.toString());
        
        Path docsPath = tempDir.resolve("docs");
        assertFalse(Files.exists(docsPath), "docs directory should not exist initially");
        
        // Start watcher - this should create the docs directory
        watcherService.start();
        
        // Wait a bit for the watcher to initialize
        Thread.sleep(500);
        
        assertTrue(Files.exists(docsPath), "docs directory should be created");
        assertTrue(Files.isDirectory(docsPath), "docs should be a directory");
    }

    @Test
    @Disabled("Temporarily disabled - needs configuration fix for tempDir")
    void testFileModificationTriggersCallback(@TempDir Path tempDir) throws IOException, InterruptedException {
        // Set up working directory
        System.setProperty("user.dir", tempDir.toString());
        
        // Create docs directory and a test file
        Path docsPath = tempDir.resolve("docs");
        Files.createDirectories(docsPath);
        Path testFile = docsPath.resolve("test.adoc");
        Files.writeString(testFile, "= Test Document\nInitial content");
        
        // Start watcher
        watcherService.start();
        
        // Wait for watcher to initialize
        Thread.sleep(500);
        
        // Reset callback counter and latch
        callbackCount.set(0);
        callbackLatch = new CountDownLatch(1);
        
        // Modify the file using atomic write to ensure the event is triggered
        Files.writeString(testFile, "= Test Document\nModified content at " + System.currentTimeMillis());
        
        // Wait for callback with timeout
        boolean callbackReceived = callbackLatch.await(3, TimeUnit.SECONDS);
        
        assertTrue(callbackReceived, "Callback should be triggered by file modification");
        assertTrue(callbackCount.get() > 0, "Callback should be called at least once");
    }

    @Test
    @Disabled("Temporarily disabled - needs configuration fix for tempDir")
    void testFileCreationTriggersCallback(@TempDir Path tempDir) throws IOException, InterruptedException {
        System.setProperty("user.dir", tempDir.toString());
        
        // Create docs directory
        Path docsPath = tempDir.resolve("docs");
        Files.createDirectories(docsPath);
        
        // Start watcher
        watcherService.start();
        
        // Wait for watcher to initialize
        Thread.sleep(500);
        
        // Reset callback counter and latch
        callbackCount.set(0);
        callbackLatch = new CountDownLatch(1);
        
        // Create a new file
        Path newFile = docsPath.resolve("new-file-" + System.currentTimeMillis() + ".adoc");
        Files.writeString(newFile, "= New Document\nNew content");
        
        // Wait for callback with timeout
        boolean callbackReceived = callbackLatch.await(3, TimeUnit.SECONDS);
        
        assertTrue(callbackReceived, "Callback should be triggered by file creation");
        assertTrue(callbackCount.get() > 0, "Callback should be called at least once");
    }

    @Test
    @Disabled("Temporarily disabled - needs configuration fix for tempDir")
    void testFileDeletionTriggersCallback(@TempDir Path tempDir) throws IOException, InterruptedException {
        System.setProperty("user.dir", tempDir.toString());
        
        // Create docs directory and a test file
        Path docsPath = tempDir.resolve("docs");
        Files.createDirectories(docsPath);
        Path testFile = docsPath.resolve("test-" + System.currentTimeMillis() + ".adoc");
        Files.writeString(testFile, "= Test Document\nContent to be deleted");
        
        // Start watcher
        watcherService.start();
        
        // Wait for watcher to initialize
        Thread.sleep(500);
        
        // Reset callback counter and latch
        callbackCount.set(0);
        callbackLatch = new CountDownLatch(1);
        
        // Delete the file
        Files.delete(testFile);
        
        // Wait for callback with timeout
        boolean callbackReceived = callbackLatch.await(3, TimeUnit.SECONDS);
        
        assertTrue(callbackReceived, "Callback should be triggered by file deletion");
        assertTrue(callbackCount.get() > 0, "Callback should be called at least once");
    }

    @Test
    @Disabled("Temporarily disabled - needs configuration fix for tempDir")
    void testDebouncingPreventsExcessiveCallbacks(@TempDir Path tempDir) throws IOException, InterruptedException {
        System.setProperty("user.dir", tempDir.toString());
        
        // Create docs directory and a test file
        Path docsPath = tempDir.resolve("docs");
        Files.createDirectories(docsPath);
        Path testFile = docsPath.resolve("test-" + System.currentTimeMillis() + ".adoc");
        Files.writeString(testFile, "= Test Document\nInitial content");
        
        // Start watcher
        watcherService.start();
        
        // Wait for watcher to initialize
        Thread.sleep(500);
        
        // Reset callback counter
        callbackCount.set(0);
        
        // Make multiple rapid changes
        for (int i = 0; i < 5; i++) {
            Files.writeString(testFile, "= Test Document\nContent change " + i + " at " + System.currentTimeMillis());
            Thread.sleep(10); // Small delay between changes
        }
        
        // Wait for debounce period plus some buffer
        Thread.sleep(testConfig.debounceMs() + 200);
        
        // Due to debouncing, we should have fewer callbacks than changes
        int finalCallbackCount = callbackCount.get();
        assertTrue(finalCallbackCount > 0, "At least one callback should be triggered");
        assertTrue(finalCallbackCount < 5, "Debouncing should prevent a callback for every change, got: " + finalCallbackCount);
    }

    /**
     * Integration test that tests the file watching with real file system operations
     * similar to how it would work in actual usage.
     */
    @Test
    @Disabled("Temporarily disabled - needs configuration fix for tempDir")
    void testIntegrationWithRealFileOperations(@TempDir Path tempDir) throws IOException, InterruptedException {
        System.setProperty("user.dir", tempDir.toString());
        
        // Start with no docs directory
        Path docsPath = tempDir.resolve("docs");
        assertFalse(Files.exists(docsPath));
        
        // Start watcher - should create docs directory
        watcherService.start();
        Thread.sleep(500);
        
        // Verify docs directory was created
        assertTrue(Files.exists(docsPath));
        
        // Reset counter
        callbackCount.set(0);
        
        // Test a complete workflow: create file, modify it, delete it
        Path testFile = docsPath.resolve("workflow-test.adoc");
        
        // 1. Create file
        callbackLatch = new CountDownLatch(1);
        Files.writeString(testFile, "= Workflow Test\nStep 1: Created");
        assertTrue(callbackLatch.await(2, TimeUnit.SECONDS), "File creation should trigger callback");
        
        // 2. Modify file
        callbackLatch = new CountDownLatch(1);
        callbackCount.set(0);
        Files.writeString(testFile, "= Workflow Test\nStep 2: Modified");
        assertTrue(callbackLatch.await(2, TimeUnit.SECONDS), "File modification should trigger callback");
        
        // 3. Delete file
        callbackLatch = new CountDownLatch(1);
        callbackCount.set(0);
        Files.delete(testFile);
        assertTrue(callbackLatch.await(2, TimeUnit.SECONDS), "File deletion should trigger callback");
        
        // Verify callback was called for each operation
        assertTrue(callbackCount.get() > 0, "Final callback count should be positive");
    }
}