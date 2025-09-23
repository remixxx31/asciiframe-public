package co.remi.asciiframe;

import java.util.Map;

/**
 * Configuration for document footers in both HTML and PDF outputs
 */
record FooterConfig(
    String htmlText,
    String pdfText,
    String htmlPosition,
    String pdfPosition,
    String htmlAlignment,
    String pdfAlignment,
    String htmlStyle,
    String pdfStyle
) {
    
    public static FooterConfig fromMap(Map<String, Object> map) {
        if (map == null) {
            return defaultFooter();
        }
        
        return new FooterConfig(
            (String) map.get("htmlText"),
            (String) map.get("pdfText"),
            (String) map.getOrDefault("htmlPosition", "bottom"),
            (String) map.getOrDefault("pdfPosition", "footer"),
            (String) map.getOrDefault("htmlAlignment", "center"),
            (String) map.getOrDefault("pdfAlignment", "center"),
            (String) map.get("htmlStyle"),
            (String) map.get("pdfStyle")
        );
    }
    
    public static FooterConfig defaultFooter() {
        return new FooterConfig(null, null, "bottom", "footer", "center", "center", null, null);
    }
    
    public boolean hasHtmlFooter() {
        return htmlText != null && !htmlText.isEmpty();
    }
    
    public boolean hasPdfFooter() {
        return pdfText != null && !pdfText.isEmpty();
    }
}

public record ThemeConfig(
    String html,
    String pdf,
    String customCssPath,
    String customPdfThemePath,
    FooterConfig footer
) {
    
    public static ThemeConfig fromMap(Map<String, Object> map) {
        Map<String, Object> footerMap = (Map<String, Object>) map.get("footer");
        
        return new ThemeConfig(
            (String) map.getOrDefault("html", "default"),
            (String) map.getOrDefault("pdf", "default"),
            (String) map.get("customCssPath"),
            (String) map.get("customPdfThemePath"),
            FooterConfig.fromMap(footerMap)
        );
    }
    
    public static ThemeConfig defaultTheme() {
        return new ThemeConfig("default", "default", null, null, FooterConfig.defaultFooter());
    }
}