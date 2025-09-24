
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
            if (IS_CI) {
                System.out.println("ℹ️ CI environment detected, using polling fallback for file watching");
                exec.submit(this::pollingLoop);
            } else {
                exec.submit(this::loop);
            }
        }
    }
    
    private static boolean detectCI() {
        return System.getenv("CI") != null || 
               System.getenv("GITHUB_ACTIONS") != null ||
               System.getenv("JENKINS_URL") != null ||
               System.getenv("TRAVIS") != null ||
               System.getenv("CIRCLECI") != null ||
               System.getProperty("java.awt.headless", "false").equals("true");
    }
    
    private void ensureDocsDirectoryExists() {
        try {
            String docsPath = cfg.includePaths().isEmpty() ? "docs" : cfg.includePaths().get(0);
            Path p = Paths.get(docsPath).toAbsolutePath().normalize();
            
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
            Path p = Paths.get(docsPath).toAbsolutePath().normalize();
            
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
        Path p = Paths.get(docsPath).toAbsolutePath().normalize();
        
        if (!Files.exists(p) || !Files.isDirectory(p)) {
            System.err.println("❌ Directory does not exist for polling: " + p);
            return;
        }
        
        System.out.println("ℹ️ Starting polling-based file watching for: " + p);
        long lastModified = getDirectoryLastModified(p);
        
        try {
            while (true) {
                Thread.sleep(100); // Poll every 100ms
                long currentModified = getDirectoryLastModified(p);
                
                if (currentModified > lastModified) {
                    lastModified = currentModified;
                    
                    // Trigger debounced callback
                    if (pending != null) pending.cancel(false);
                    pending = exec.schedule(() -> onChange.get(), cfg.debounceMs(), TimeUnit.MILLISECONDS);
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
            return Files.walk(dir, 1)
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis();
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .max()
                    .orElse(System.currentTimeMillis());
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
}
