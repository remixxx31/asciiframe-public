package co.remi.asciiframe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.assertj.core.api.Assertions.*;
import org.asciidoctor.Attributes;

@DisplayName("ThemeManager Tests")
class ThemeManagerTest {

    @BeforeEach
    void setUp() {
        ThemeManager.cleanup();
    }

    @AfterEach
    void tearDown() {
        ThemeManager.cleanup();
    }

    @Test
    @DisplayName("Should build HTML attributes without footer")
    void shouldBuildHtmlAttributesWithoutFooter() {
        FooterConfig footerConfig = FooterConfig.defaultFooter();
        ThemeConfig themeConfig = new ThemeConfig("default", "default", null, null, footerConfig);
        
        Attributes attributes = ThemeManager.buildHtmlAttributes(themeConfig);
        
        assertThat(attributes).isNotNull();
        assertThat(attributes.map().get("icons")).isEqualTo("font");
        assertThat(attributes.map().get("source-highlighter")).isEqualTo("rouge");
        assertThat(attributes.map().get("toc")).isEqualTo("left");
    }

    @Test
    @DisplayName("Should build PDF attributes without footer")
    void shouldBuildPdfAttributesWithoutFooter() {
        FooterConfig footerConfig = FooterConfig.defaultFooter();
        ThemeConfig themeConfig = new ThemeConfig("default", "default", null, null, footerConfig);
        
        Attributes attributes = ThemeManager.buildPdfAttributes(themeConfig);
        
        assertThat(attributes).isNotNull();
        assertThat(attributes.map().get("title-page")).isEqualTo("");
        assertThat(attributes.map().get("toc")).isEqualTo("");
    }

    @Test
    @DisplayName("Should build PDF attributes with footer configuration")
    void shouldBuildPdfAttributesWithFooterConfiguration() {
        FooterConfig footerConfig = new FooterConfig(
            null, "Page {page-number} sur {page-count}", 
            "bottom", "footer", "center", "center", null, null
        );
        ThemeConfig themeConfig = new ThemeConfig("default", "default", null, null, footerConfig);
        
        Attributes attributes = ThemeManager.buildPdfAttributes(themeConfig);
        
        assertThat(attributes).isNotNull();
        assertThat(attributes.map().get("pdf-footer-center")).isEqualTo("Page {page-number} sur {page-count}");
        assertThat(attributes.map().get("pdf-footer-left")).isEqualTo("");
        assertThat(attributes.map().get("pdf-footer-right")).isEqualTo("");
    }

    @Test
    @DisplayName("Should configure PDF footer with left alignment")
    void shouldConfigurePdfFooterWithLeftAlignment() {
        FooterConfig footerConfig = new FooterConfig(
            null, "© 2024 Mon Entreprise", 
            "bottom", "footer", "center", "left", null, null
        );
        ThemeConfig themeConfig = new ThemeConfig("default", "default", null, null, footerConfig);
        
        Attributes attributes = ThemeManager.buildPdfAttributes(themeConfig);
        
        assertThat(attributes.map().get("pdf-footer-left")).isEqualTo("© 2024 Mon Entreprise");
        assertThat(attributes.map().get("pdf-footer-center")).isEqualTo("");
        assertThat(attributes.map().get("pdf-footer-right")).isEqualTo("");
    }

    @Test
    @DisplayName("Should configure PDF footer with right alignment")
    void shouldConfigurePdfFooterWithRightAlignment() {
        FooterConfig footerConfig = new FooterConfig(
            null, "Generated {localdate}", 
            "bottom", "footer", "center", "right", null, null
        );
        ThemeConfig themeConfig = new ThemeConfig("default", "default", null, null, footerConfig);
        
        Attributes attributes = ThemeManager.buildPdfAttributes(themeConfig);
        
        assertThat(attributes.map().get("pdf-footer-right")).isEqualTo("Generated {localdate}");
        assertThat(attributes.map().get("pdf-footer-center")).isEqualTo("");
        assertThat(attributes.map().get("pdf-footer-left")).isEqualTo("");
    }

    @Test
    @DisplayName("Should add PDF footer style attribute when provided")
    void shouldAddPdfFooterStyleAttributeWhenProvided() {
        FooterConfig footerConfig = new FooterConfig(
            null, "Footer text", 
            "bottom", "footer", "center", "center", null, "font-size: 9pt; color: #666666"
        );
        ThemeConfig themeConfig = new ThemeConfig("default", "default", null, null, footerConfig);
        
        Attributes attributes = ThemeManager.buildPdfAttributes(themeConfig);
        
        assertThat(attributes.map().get("pdf-footer-style")).isEqualTo("font-size: 9pt; color: #666666");
    }

    @Test
    @DisplayName("Should inject HTML footer into HTML content")
    void shouldInjectHtmlFooterIntoHtmlContent() {
        FooterConfig footerConfig = new FooterConfig(
            "© 2024 Test Footer", null, 
            "bottom", "footer", "center", "center", null, null
        );
        ThemeConfig themeConfig = new ThemeConfig("default", "default", null, null, footerConfig);
        
        // Build attributes to store footer config
        ThemeManager.buildHtmlAttributes(themeConfig);
        
        String originalHtml = "<html><head></head><body><h1>Test</h1></body></html>";
        String result = ThemeManager.injectThemeCss(originalHtml);
        
        assertThat(result).contains("asciiframe-footer");
        assertThat(result).contains("© 2024 Test Footer");
        assertThat(result).contains("text-align: center;");
    }

    @Test
    @DisplayName("Should inject HTML footer with custom alignment")
    void shouldInjectHtmlFooterWithCustomAlignment() {
        FooterConfig footerConfig = new FooterConfig(
            "Left aligned footer", null, 
            "bottom", "footer", "left", "center", null, null
        );
        ThemeConfig themeConfig = new ThemeConfig("default", "default", null, null, footerConfig);
        
        ThemeManager.buildHtmlAttributes(themeConfig);
        
        String originalHtml = "<html><body><h1>Test</h1></body></html>";
        String result = ThemeManager.injectThemeCss(originalHtml);
        
        assertThat(result).contains("text-align: left;");
        assertThat(result).contains("Left aligned footer");
    }

    @Test
    @DisplayName("Should inject HTML footer with fixed position")
    void shouldInjectHtmlFooterWithFixedPosition() {
        FooterConfig footerConfig = new FooterConfig(
            "Fixed footer", null, 
            "fixed-bottom", "footer", "center", "center", null, null
        );
        ThemeConfig themeConfig = new ThemeConfig("default", "default", null, null, footerConfig);
        
        ThemeManager.buildHtmlAttributes(themeConfig);
        
        String originalHtml = "<html><body><h1>Test</h1></body></html>";
        String result = ThemeManager.injectThemeCss(originalHtml);
        
        assertThat(result).contains("position: fixed;");
        assertThat(result).contains("bottom: 0;");
        assertThat(result).contains("Fixed footer");
    }

    @Test
    @DisplayName("Should inject HTML footer with custom style")
    void shouldInjectHtmlFooterWithCustomStyle() {
        FooterConfig footerConfig = new FooterConfig(
            "Styled footer", null, 
            "bottom", "footer", "center", "center", 
            "color: red; font-weight: bold;", null
        );
        ThemeConfig themeConfig = new ThemeConfig("default", "default", null, null, footerConfig);
        
        ThemeManager.buildHtmlAttributes(themeConfig);
        
        String originalHtml = "<html><body><h1>Test</h1></body></html>";
        String result = ThemeManager.injectThemeCss(originalHtml);
        
        assertThat(result).contains("color: red; font-weight: bold;");
        assertThat(result).contains("Styled footer");
    }

    @Test
    @DisplayName("Should process footer variables in HTML footer text")
    void shouldProcessFooterVariablesInHtmlFooterText() {
        FooterConfig footerConfig = new FooterConfig(
            "Generated on {date} at {time}", null, 
            "bottom", "footer", "center", "center", null, null
        );
        ThemeConfig themeConfig = new ThemeConfig("default", "default", null, null, footerConfig);
        
        ThemeManager.buildHtmlAttributes(themeConfig);
        
        String originalHtml = "<html><body><h1>Test</h1></body></html>";
        String result = ThemeManager.injectThemeCss(originalHtml);
        
        assertThat(result).contains("Generated on");
        assertThat(result).doesNotContain("{date}");
        assertThat(result).doesNotContain("{time}");
        // Check for proper date/time format - the pattern should match DD/MM/YYYY HH:MM
        assertThat(result).containsPattern("Generated on \\d{2}/\\d{2}/\\d{4} at \\d{2}:\\d{2}");
    }

    @Test
    @DisplayName("Should not inject footer when no HTML footer text is provided")
    void shouldNotInjectFooterWhenNoHtmlFooterTextIsProvided() {
        FooterConfig footerConfig = new FooterConfig(
            null, "PDF footer only", 
            "bottom", "footer", "center", "center", null, null
        );
        ThemeConfig themeConfig = new ThemeConfig("default", "default", null, null, footerConfig);
        
        ThemeManager.buildHtmlAttributes(themeConfig);
        
        String originalHtml = "<html><body><h1>Test</h1></body></html>";
        String result = ThemeManager.injectThemeCss(originalHtml);
        
        assertThat(result).doesNotContain("asciiframe-footer");
        assertThat(result).doesNotContain("PDF footer only");
    }

    @Test
    @DisplayName("Should inject footer at end when no body tag present")
    void shouldInjectFooterAtEndWhenNoBodyTagPresent() {
        FooterConfig footerConfig = new FooterConfig(
            "Footer without body tag", null, 
            "bottom", "footer", "center", "center", null, null
        );
        ThemeConfig themeConfig = new ThemeConfig("default", "default", null, null, footerConfig);
        
        ThemeManager.buildHtmlAttributes(themeConfig);
        
        String originalHtml = "<h1>Test without body tag</h1>";
        String result = ThemeManager.injectThemeCss(originalHtml);
        
        assertThat(result).endsWith("Footer without body tag</div>");
    }
}