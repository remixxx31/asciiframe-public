
package co.remi.asciiframe;

import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record Config(
    String entry,
    String outDir,
    List<String> formats,
    String krokiUrl,
    boolean watchEnabled,
    int debounceMs,
    int port,
    List<String> includePaths,
    String diagramsCacheDir,
    ThemeConfig theme
) {
    public static Config load() {
        String path = System.getenv().getOrDefault("CONFIG_PATH", "config.yml");
        try (InputStream in = java.nio.file.Files.newInputStream(Path.of(path))) {
            Map<String,Object> y = new Yaml().load(in);
            Map<String,Object> diagrams = (Map<String,Object>) y.getOrDefault("diagrams", Map.of());
            Map<String,Object> watch = (Map<String,Object>) y.getOrDefault("watch", Map.of());
            Map<String,Object> server = (Map<String,Object>) y.getOrDefault("server", Map.of());
            Map<String,Object> cache = (Map<String,Object>) y.getOrDefault("cache", Map.of());
            Map<String,Object> security = (Map<String,Object>) y.getOrDefault("security", Map.of());
            Map<String,Object> theme = (Map<String,Object>) y.getOrDefault("theme", Map.of());

            return new Config(
                (String) y.getOrDefault("entry", "docs/index.adoc"),
                (String) y.getOrDefault("outDir", "build"),
                (List<String>) y.getOrDefault("formats", List.of("html","pdf")),
                (String) diagrams.getOrDefault("url", "http://localhost:8000"),
                (Boolean) watch.getOrDefault("enabled", true),
                ((Number) watch.getOrDefault("debounceMs", 300)).intValue(),
                ((Number) server.getOrDefault("port", 8080)).intValue(),
                (List<String>) security.getOrDefault("includePaths", List.of("docs")),
                (String) cache.getOrDefault("diagramsDir", ".cache/diagrams"),
                ThemeConfig.fromMap(theme)
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config: " + e.getMessage(), e);
        }
    }
}
