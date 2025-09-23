package co.remi.asciiframe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic unit tests for WatcherService that don't rely on file system watching,
 * which can be unreliable in test environments.
 */
class WatcherServiceBasicTest {

    private Config testConfig;
    private AtomicInteger callbackCount;

    @BeforeEach
    void setUp() {
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
    }

    @Test
    void testWatcherServiceCanBeCreatedWithCallback() {
        Runnable callback = () -> callbackCount.incrementAndGet();
        WatcherService watcherService = new WatcherService(testConfig, callback);
        
        assertNotNull(watcherService);
        assertEquals(0, callbackCount.get(), "Callback should not be called during creation");
    }

    @Test
    void testWatcherServiceCanBeCreatedWithNullCallback() {
        // Test that we can create a watcher service with a null callback
        // (though this might not be practical, it shouldn't crash)
        assertDoesNotThrow(() -> {
            WatcherService watcherService = new WatcherService(testConfig, null);
            assertNotNull(watcherService);
        });
    }

    @Test
    void testWatcherServiceCanBeStarted() {
        Runnable callback = () -> callbackCount.incrementAndGet();
        WatcherService watcherService = new WatcherService(testConfig, callback);
        
        // Starting the watcher should not throw an exception
        assertDoesNotThrow(() -> watcherService.start());
    }

    @Test
    void testWatcherServiceStartIsIdempotent() {
        Runnable callback = () -> callbackCount.incrementAndGet();
        WatcherService watcherService = new WatcherService(testConfig, callback);
        
        // Multiple starts should not cause issues
        assertDoesNotThrow(() -> {
            watcherService.start();
            watcherService.start();
            watcherService.start();
        });
    }

    @Test
    void testWatcherServiceUsesConfigDebounceTime() {
        // Test with different debounce times
        Config shortDebounceConfig = new Config(
            "docs/index.adoc",
            "build_artifacts", 
            List.of("html"),
            "http://localhost:8000",
            true,
            10, // Very short debounce
            8080,
            List.of("docs"),
            ".cache/diagrams",
            null
        );

        Config longDebounceConfig = new Config(
            "docs/index.adoc",
            "build_artifacts",
            List.of("html"),
            "http://localhost:8000",
            true,
            1000, // Long debounce
            8080,
            List.of("docs"),
            ".cache/diagrams",
            null
        );

        Runnable callback = () -> callbackCount.incrementAndGet();
        
        // Both should be creatable
        assertDoesNotThrow(() -> {
            WatcherService shortWatcher = new WatcherService(shortDebounceConfig, callback);
            WatcherService longWatcher = new WatcherService(longDebounceConfig, callback);
            
            assertNotNull(shortWatcher);
            assertNotNull(longWatcher);
        });
    }

    @Test
    void testWatcherServiceWorksWithDisabledWatch() {
        Config disabledWatchConfig = new Config(
            "docs/index.adoc",
            "build_artifacts",
            List.of("html", "pdf"),
            "http://localhost:8000",
            false, // Watch disabled
            50,
            8080,
            List.of("docs"),
            ".cache/diagrams",
            null
        );

        Runnable callback = () -> callbackCount.incrementAndGet();
        WatcherService watcherService = new WatcherService(disabledWatchConfig, callback);
        
        assertNotNull(watcherService);
        assertDoesNotThrow(() -> watcherService.start());
    }
}