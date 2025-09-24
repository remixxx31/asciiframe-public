
package co.remi.asciiframe;

import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class WatcherService {
    private final Config cfg;
    private final Supplier<Void> onChange;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean started = false;
    private ScheduledFuture<?> pending;
    private volatile boolean useFallbackPolling = false;
    private static final boolean IS_CI = detectCI();
    
    // Debug tracking fields for tests
    public static volatile String lastDebugMessage = "";
    public static volatile boolean pollingStarted = false;
    public static volatile long pollCount = 0;
    public static volatile long changeDetectionCount = 0;
    public static volatile long callbackExecutionCount = 0;

    public WatcherService(Config cfg, Runnable callback) {
        this.cfg = cfg;
        this.onChange = () -> { callback.run(); return null; };
    }

    public void start() {
        if (started) return;
        started = true;
        
        // Create docs directory synchronously first
        ensureDocsDirectoryExists();
        
        // Only start file watching if enabled in config
        if (cfg.watchEnabled()) {
            // Force polling for all environments until tests are stable
            lastDebugMessage = "WatcherService: Using forced polling mode, watchEnabled=" + cfg.watchEnabled();
            exec.submit(this::pollingLoop);
        } else {
            lastDebugMessage = "WatcherService: File watching is disabled in config, watchEnabled=" + cfg.watchEnabled();
        }
    }
    
    private static boolean detectCI() {
        boolean isCI = System.getenv("CI") != null || 
                      System.getenv("GITHUB_ACTIONS") != null ||
                      System.getenv("JENKINS_URL") != null ||
                      System.getenv("TRAVIS") != null ||
                      System.getenv("CIRCLECI") != null ||
                      System.getenv("BUILD_NUMBER") != null ||
                      "true".equals(System.getenv("CI")) ||
                      "true".equals(System.getenv("CONTINUOUS_INTEGRATION")) ||
                      System.getProperty("java.awt.headless", "false").equals("true");
        
        if (isCI) {
            System.err.println("ℹ️ CI environment detected - CI=" + System.getenv("CI") + 
                             ", GITHUB_ACTIONS=" + System.getenv("GITHUB_ACTIONS"));
        }
        
        return isCI;
    }
    
    private void ensureDocsDirectoryExists() {
        try {
            String docsPath = cfg.includePaths().isEmpty() ? "docs" : cfg.includePaths().get(0);
            
            Path p;
            if (Paths.get(docsPath).isAbsolute()) {
                p = Paths.get(docsPath).normalize();
            } else {
                // For relative paths, resolve against current user.dir (handles test scenarios)
                String userDir = System.getProperty("user.dir");
                p = Paths.get(userDir).resolve(docsPath).normalize();
            }
            
            if (!Files.exists(p)) {
                Files.createDirectories(p);
                System.out.println("ℹ️ Created docs directory: " + p);
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to create docs directory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loop() {
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {
            String docsPath = cfg.includePaths().isEmpty() ? "docs" : cfg.includePaths().get(0);
            
            Path p;
            if (Paths.get(docsPath).isAbsolute()) {
                p = Paths.get(docsPath).normalize();
            } else {
                String userDir = System.getProperty("user.dir");
                p = Paths.get(userDir).resolve(docsPath).normalize();
            }
            
            // Verify the directory exists (should have been created in start())
            if (!Files.exists(p) || !Files.isDirectory(p)) {
                System.err.println("❌ Directory does not exist or is not a directory: " + p);
                return;
            }
            
            System.out.println("ℹ️ Registering file watcher for: " + p);
            p.register(ws, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            System.out.println("ℹ️ File watcher registered successfully");
            
            while (true) {
                WatchKey key = ws.take();
                if (pending != null) pending.cancel(false);
                pending = exec.schedule(() -> onChange.get(), cfg.debounceMs(), TimeUnit.MILLISECONDS);
                key.reset();
            }
        } catch (Exception e) {
            System.err.println("❌ WatcherService loop failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void pollingLoop() {
        String docsPath = cfg.includePaths().isEmpty() ? "docs" : cfg.includePaths().get(0);
        
        Path p;
        if (Paths.get(docsPath).isAbsolute()) {
            p = Paths.get(docsPath).normalize();
        } else {
            String userDir = System.getProperty("user.dir");
            p = Paths.get(userDir).resolve(docsPath).normalize();
        }
        
        if (!Files.exists(p) || !Files.isDirectory(p)) {
            System.err.println("❌ Directory does not exist for polling: " + p);
            return;
        }
        
        pollingStarted = true;
        lastDebugMessage = "Starting polling for directory: " + p;
        long lastModified = getDirectoryLastModified(p);
        lastDebugMessage += ", initial lastModified: " + lastModified;
        
        try {
            while (true) {
                Thread.sleep(50); // Poll every 50ms for faster response
                pollCount++;
                long currentModified = getDirectoryLastModified(p);
                
                if (currentModified > lastModified) {
                    changeDetectionCount++;
                    lastDebugMessage = "File change detected! Poll#" + pollCount + " New: " + currentModified + ", Old: " + lastModified;
                    lastModified = currentModified;
                    
                    // Trigger debounced callback
                    if (pending != null) pending.cancel(false);
                    pending = exec.schedule(() -> {
                        callbackExecutionCount++;
                        lastDebugMessage += " | Callback executed #" + callbackExecutionCount;
                        return onChange.get();
                    }, cfg.debounceMs(), TimeUnit.MILLISECONDS);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("ℹ️ Polling loop interrupted");
        } catch (Exception e) {
            System.err.println("❌ Polling loop failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private long getDirectoryLastModified(Path dir) {
        try {
            long maxModified = Files.walk(dir, 1)
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            long modified = Files.getLastModifiedTime(path).toMillis();
                            return modified;
                        } catch (Exception e) {
                            System.out.println("WatcherService: Error reading lastModified for " + path + ": " + e.getMessage());
                            return 0;
                        }
                    })
                    .max()
                    .orElse(0L);
            
            // If no files exist, return directory's own modification time
            if (maxModified == 0L) {
                try {
                    maxModified = Files.getLastModifiedTime(dir).toMillis();
                } catch (Exception e) {
                    System.out.println("WatcherService: Error reading directory lastModified: " + e.getMessage());
                    maxModified = System.currentTimeMillis();
                }
            }
            
            return maxModified;
        } catch (Exception e) {
            System.out.println("WatcherService: Error in getDirectoryLastModified: " + e.getMessage());
            return System.currentTimeMillis();
        }
    }
}
