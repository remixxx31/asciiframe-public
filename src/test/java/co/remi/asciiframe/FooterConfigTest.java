package co.remi.asciiframe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import java.util.HashMap;

@DisplayName("FooterConfig Tests")
class FooterConfigTest {

    @Test
    @DisplayName("Should create default footer configuration")
    void shouldCreateDefaultFooterConfig() {
        FooterConfig config = FooterConfig.defaultFooter();
        
        assertThat(config.htmlText()).isNull();
        assertThat(config.pdfText()).isNull();
        assertThat(config.htmlPosition()).isEqualTo("bottom");
        assertThat(config.pdfPosition()).isEqualTo("footer");
        assertThat(config.htmlAlignment()).isEqualTo("center");
        assertThat(config.pdfAlignment()).isEqualTo("center");
        assertThat(config.htmlStyle()).isNull();
        assertThat(config.pdfStyle()).isNull();
    }

    @Test
    @DisplayName("Should create footer configuration from map")
    void shouldCreateFooterConfigFromMap() {
        Map<String, Object> footerMap = new HashMap<>();
        footerMap.put("htmlText", "© 2024 Mon Entreprise");
        footerMap.put("pdfText", "Page {page-number} sur {page-count}");
        footerMap.put("htmlPosition", "fixed-bottom");
        footerMap.put("pdfPosition", "bottom");
        footerMap.put("htmlAlignment", "left");
        footerMap.put("pdfAlignment", "right");
        footerMap.put("htmlStyle", "color: #666; font-size: 12px;");
        footerMap.put("pdfStyle", "font-size: 10pt; color: #666666");
        
        FooterConfig config = FooterConfig.fromMap(footerMap);
        
        assertThat(config.htmlText()).isEqualTo("© 2024 Mon Entreprise");
        assertThat(config.pdfText()).isEqualTo("Page {page-number} sur {page-count}");
        assertThat(config.htmlPosition()).isEqualTo("fixed-bottom");
        assertThat(config.pdfPosition()).isEqualTo("bottom");
        assertThat(config.htmlAlignment()).isEqualTo("left");
        assertThat(config.pdfAlignment()).isEqualTo("right");
        assertThat(config.htmlStyle()).isEqualTo("color: #666; font-size: 12px;");
        assertThat(config.pdfStyle()).isEqualTo("font-size: 10pt; color: #666666");
    }

    @Test
    @DisplayName("Should use default values when map values are missing")
    void shouldUseDefaultValuesWhenMapValuesAreMissing() {
        Map<String, Object> footerMap = new HashMap<>();
        footerMap.put("htmlText", "Only HTML text set");
        
        FooterConfig config = FooterConfig.fromMap(footerMap);
        
        assertThat(config.htmlText()).isEqualTo("Only HTML text set");
        assertThat(config.pdfText()).isNull();
        assertThat(config.htmlPosition()).isEqualTo("bottom");
        assertThat(config.pdfPosition()).isEqualTo("footer");
        assertThat(config.htmlAlignment()).isEqualTo("center");
        assertThat(config.pdfAlignment()).isEqualTo("center");
    }

    @Test
    @DisplayName("Should return default config when map is null")
    void shouldReturnDefaultConfigWhenMapIsNull() {
        FooterConfig config = FooterConfig.fromMap(null);
        FooterConfig defaultConfig = FooterConfig.defaultFooter();
        
        assertThat(config).isEqualTo(defaultConfig);
    }

    @Test
    @DisplayName("Should correctly detect HTML footer presence")
    void shouldCorrectlyDetectHtmlFooterPresence() {
        // Footer with HTML text
        FooterConfig configWithHtml = new FooterConfig(
            "HTML footer text", null, "bottom", "footer", "center", "center", null, null
        );
        assertThat(configWithHtml.hasHtmlFooter()).isTrue();
        
        // Footer without HTML text
        FooterConfig configWithoutHtml = new FooterConfig(
            null, "PDF footer text", "bottom", "footer", "center", "center", null, null
        );
        assertThat(configWithoutHtml.hasHtmlFooter()).isFalse();
        
        // Footer with empty HTML text
        FooterConfig configWithEmptyHtml = new FooterConfig(
            "", "PDF footer text", "bottom", "footer", "center", "center", null, null
        );
        assertThat(configWithEmptyHtml.hasHtmlFooter()).isFalse();
    }

    @Test
    @DisplayName("Should correctly detect PDF footer presence")
    void shouldCorrectlyDetectPdfFooterPresence() {
        // Footer with PDF text
        FooterConfig configWithPdf = new FooterConfig(
            null, "PDF footer text", "bottom", "footer", "center", "center", null, null
        );
        assertThat(configWithPdf.hasPdfFooter()).isTrue();
        
        // Footer without PDF text
        FooterConfig configWithoutPdf = new FooterConfig(
            "HTML footer text", null, "bottom", "footer", "center", "center", null, null
        );
        assertThat(configWithoutPdf.hasPdfFooter()).isFalse();
        
        // Footer with empty PDF text
        FooterConfig configWithEmptyPdf = new FooterConfig(
            "HTML footer text", "", "bottom", "footer", "center", "center", null, null
        );
        assertThat(configWithEmptyPdf.hasPdfFooter()).isFalse();
    }

    @Test
    @DisplayName("Should handle both HTML and PDF footers simultaneously")
    void shouldHandleBothHtmlAndPdfFootersSimultaneously() {
        FooterConfig config = new FooterConfig(
            "HTML footer", "PDF footer", "bottom", "footer", "center", "center", null, null
        );
        
        assertThat(config.hasHtmlFooter()).isTrue();
        assertThat(config.hasPdfFooter()).isTrue();
    }
}