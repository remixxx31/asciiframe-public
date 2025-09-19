package co.remi.asciiframe;

import java.util.Map;

public record ThemeConfig(
    String html,
    String pdf,
    String customCssPath
) {
    
    public static ThemeConfig fromMap(Map<String, Object> map) {
        return new ThemeConfig(
            (String) map.getOrDefault("html", "default"),
            (String) map.getOrDefault("pdf", "default"),
            (String) map.get("customCssPath")
        );
    }
    
    public static ThemeConfig defaultTheme() {
        return new ThemeConfig("default", "default", null);
    }
}