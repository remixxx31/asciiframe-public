
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

    public WatcherService(Config cfg, Runnable callback) {
        this.cfg = cfg;
        this.onChange = () -> { callback.run(); return null; };
    }

    public void start() {
        if (started) return;
        started = true;
        
        // Only start file watching if enabled in config
        if (cfg.watchEnabled()) {
            exec.submit(this::loop);
        }
    }

    private void loop() {
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {
            // Use configured path instead of hardcoded "docs"
            String docsPath = cfg.includePaths().isEmpty() ? "docs" : cfg.includePaths().get(0);
            Path p = Paths.get(docsPath).toAbsolutePath().normalize();
            
            // Create the docs directory if it doesn't exist to avoid NoSuchFileException
            if (!Files.exists(p)) {
                try {
                    Files.createDirectories(p);
                    System.out.println("ℹ️ Created missing docs directory at runtime: " + p);
                } catch (Exception e) {
                    System.err.println("❌ Failed to create docs directory: " + e.getMessage());
                    // Don't continue if we can't create the directory - the registration will fail
                    return;
                }
            }
            
            // Ensure it's actually a directory
            if (!Files.isDirectory(p)) {
                System.err.println("❌ Path is not a directory: " + p);
                return;
            }
            
            p.register(ws, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            
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
}
