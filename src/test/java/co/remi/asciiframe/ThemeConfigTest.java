package co.remi.asciiframe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import java.util.HashMap;

@DisplayName("ThemeConfig Tests")
class ThemeConfigTest {

    @Test
    @DisplayName("Should create default theme configuration")
    void shouldCreateDefaultThemeConfig() {
        ThemeConfig config = ThemeConfig.defaultTheme();
        
        assertThat(config.html()).isEqualTo("default");
        assertThat(config.pdf()).isEqualTo("default");
        assertThat(config.customCssPath()).isNull();
        assertThat(config.customPdfThemePath()).isNull();
        assertThat(config.footer()).isNotNull();
        assertThat(config.footer()).isEqualTo(FooterConfig.defaultFooter());
    }

    @Test
    @DisplayName("Should create theme configuration from map")
    void shouldCreateThemeConfigFromMap() {
        Map<String, Object> themeMap = new HashMap<>();
        themeMap.put("html", "dark");
        themeMap.put("pdf", "book");
        themeMap.put("customCssPath", "themes/html/custom.css");
        themeMap.put("customPdfThemePath", "themes/pdf/custom.yml");
        
        Map<String, Object> footerMap = new HashMap<>();
        footerMap.put("htmlText", "© 2024 Test");
        footerMap.put("pdfText", "Page {page-number}");
        themeMap.put("footer", footerMap);
        
        ThemeConfig config = ThemeConfig.fromMap(themeMap);
        
        assertThat(config.html()).isEqualTo("dark");
        assertThat(config.pdf()).isEqualTo("book");
        assertThat(config.customCssPath()).isEqualTo("themes/html/custom.css");
        assertThat(config.customPdfThemePath()).isEqualTo("themes/pdf/custom.yml");
        assertThat(config.footer()).isNotNull();
        assertThat(config.footer().htmlText()).isEqualTo("© 2024 Test");
        assertThat(config.footer().pdfText()).isEqualTo("Page {page-number}");
    }

    @Test
    @DisplayName("Should use default values when map values are missing")
    void shouldUseDefaultValuesWhenMapValuesAreMissing() {
        Map<String, Object> themeMap = new HashMap<>();
        themeMap.put("html", "minimal");
        // pdf, customCssPath, customPdfThemePath, and footer are missing
        
        ThemeConfig config = ThemeConfig.fromMap(themeMap);
        
        assertThat(config.html()).isEqualTo("minimal");
        assertThat(config.pdf()).isEqualTo("default");
        assertThat(config.customCssPath()).isNull();
        assertThat(config.customPdfThemePath()).isNull();
        assertThat(config.footer()).isEqualTo(FooterConfig.defaultFooter());
    }

    @Test
    @DisplayName("Should handle empty map")
    void shouldHandleEmptyMap() {
        Map<String, Object> themeMap = new HashMap<>();
        
        ThemeConfig config = ThemeConfig.fromMap(themeMap);
        
        assertThat(config.html()).isEqualTo("default");
        assertThat(config.pdf()).isEqualTo("default");
        assertThat(config.customCssPath()).isNull();
        assertThat(config.customPdfThemePath()).isNull();
        assertThat(config.footer()).isEqualTo(FooterConfig.defaultFooter());
    }

    @Test
    @DisplayName("Should handle footer with partial configuration")
    void shouldHandleFooterWithPartialConfiguration() {
        Map<String, Object> themeMap = new HashMap<>();
        
        Map<String, Object> footerMap = new HashMap<>();
        footerMap.put("htmlText", "Only HTML configured");
        footerMap.put("htmlAlignment", "right");
        // pdfText and other fields are missing
        themeMap.put("footer", footerMap);
        
        ThemeConfig config = ThemeConfig.fromMap(themeMap);
        
        assertThat(config.footer().htmlText()).isEqualTo("Only HTML configured");
        assertThat(config.footer().htmlAlignment()).isEqualTo("right");
        assertThat(config.footer().pdfText()).isNull();
        assertThat(config.footer().pdfAlignment()).isEqualTo("center"); // default value
    }

    @Test
    @DisplayName("Should preserve all footer configuration fields")
    void shouldPreserveAllFooterConfigurationFields() {
        Map<String, Object> themeMap = new HashMap<>();
        
        Map<String, Object> footerMap = new HashMap<>();
        footerMap.put("htmlText", "HTML Footer Text");
        footerMap.put("pdfText", "PDF Footer Text");
        footerMap.put("htmlPosition", "fixed-bottom");
        footerMap.put("pdfPosition", "bottom");
        footerMap.put("htmlAlignment", "left");
        footerMap.put("pdfAlignment", "right");
        footerMap.put("htmlStyle", "color: red;");
        footerMap.put("pdfStyle", "font-size: 8pt;");
        themeMap.put("footer", footerMap);
        
        ThemeConfig config = ThemeConfig.fromMap(themeMap);
        FooterConfig footer = config.footer();
        
        assertThat(footer.htmlText()).isEqualTo("HTML Footer Text");
        assertThat(footer.pdfText()).isEqualTo("PDF Footer Text");
        assertThat(footer.htmlPosition()).isEqualTo("fixed-bottom");
        assertThat(footer.pdfPosition()).isEqualTo("bottom");
        assertThat(footer.htmlAlignment()).isEqualTo("left");
        assertThat(footer.pdfAlignment()).isEqualTo("right");
        assertThat(footer.htmlStyle()).isEqualTo("color: red;");
        assertThat(footer.pdfStyle()).isEqualTo("font-size: 8pt;");
    }
}