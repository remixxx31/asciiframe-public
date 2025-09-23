package co.remi.asciiframe;

import org.asciidoctor.Attributes;
import org.asciidoctor.AttributesBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ThemeManager {
    
    private static String currentThemeCss = "";
    private static FooterConfig currentFooterConfig = null;
    
    // Thèmes HTML prédéfinis
    private static final Map<String, String> HTML_THEMES = Map.of(
        "default", """
            <style>
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; 
                   max-width: 900px; margin: 0 auto; padding: 20px; line-height: 1.6; }
            h1, h2, h3 { color: #2c3e50; }
            .toc { background: #f8f9fa; padding: 15px; border-radius: 5px; margin-bottom: 20px; }
            code { background: #f4f4f4; padding: 2px 4px; border-radius: 3px; }
            .listingblock { background: #f8f9fa; padding: 15px; border-radius: 5px; overflow-x: auto; }
            </style>
        """,
        
        "dark", """
            <style>
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; 
                   max-width: 900px; margin: 0 auto; padding: 20px; line-height: 1.6;
                   background: #1a1a1a; color: #e1e1e1; }
            h1, h2, h3 { color: #64b5f6; }
            .toc { background: #2d2d2d; padding: 15px; border-radius: 5px; margin-bottom: 20px; }
            code { background: #2d2d2d; padding: 2px 4px; border-radius: 3px; color: #81c784; }
            .listingblock { background: #2d2d2d; padding: 15px; border-radius: 5px; overflow-x: auto; }
            a { color: #64b5f6; }
            </style>
        """,
        
        "minimal", """
            <style>
            body { font-family: Georgia, serif; max-width: 700px; margin: 0 auto; padding: 40px 20px; 
                   line-height: 1.7; color: #333; }
            h1, h2, h3 { font-weight: normal; margin-top: 40px; }
            .toc { display: none; }
            code { font-family: 'Monaco', 'Menlo', monospace; background: #f0f0f0; }
            .listingblock { background: #f9f9f9; border-left: 4px solid #ddd; padding-left: 20px; }
            </style>
        """,
        
        "documentation", """
            <style>
            body { font-family: 'Inter', system-ui, sans-serif; max-width: 1000px; margin: 0 auto; 
                   padding: 20px; line-height: 1.6; background: #fdfdfd; }
            h1 { border-bottom: 3px solid #0066cc; padding-bottom: 10px; }
            h2 { border-bottom: 1px solid #ddd; padding-bottom: 5px; margin-top: 30px; }
            .toc { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); 
                   color: white; padding: 20px; border-radius: 8px; }
            .toc a { color: white; text-decoration: none; }
            code { background: #e3f2fd; color: #1565c0; padding: 2px 6px; border-radius: 4px; }
            .listingblock { background: #263238; color: #eeffff; padding: 20px; border-radius: 8px; }
            </style>
        """,
        
        "blog", """
            <style>
            body { font-family: 'Source Serif Pro', Georgia, serif; max-width: 800px; margin: 0 auto; 
                   padding: 20px; line-height: 1.8; color: #2c3e50; }
            h1 { font-size: 2.5em; text-align: center; margin-bottom: 10px; }
            h2 { color: #e74c3c; border-bottom: 2px solid #ecf0f1; padding-bottom: 5px; }
            .toc { background: #ecf0f1; padding: 20px; border-radius: 10px; }
            blockquote { border-left: 4px solid #e74c3c; padding-left: 20px; font-style: italic; }
            code { background: #f8f9fa; color: #e74c3c; padding: 2px 5px; }
            .listingblock { background: #2c3e50; color: #ecf0f1; padding: 20px; border-radius: 5px; }
            </style>
        """
    );
    
    public static Attributes buildHtmlAttributes(ThemeConfig theme) {
        AttributesBuilder builder = AttributesBuilder.attributes();
        
        // Attributs de base
        builder.attribute("icons", "font");
        builder.attribute("source-highlighter", "rouge");
        builder.attribute("toc", "left");
        
        // CSS du thème
        String css;
        
        // CSS custom si fourni (prioritaire)
        if (theme.customCssPath() != null) {
            try {
                css = Files.readString(Path.of(theme.customCssPath()));
                System.out.println("🎨 Using custom CSS: " + theme.customCssPath());
            } catch (Exception e) {
                System.err.println("⚠️  Cannot read custom CSS: " + e.getMessage());
                css = HTML_THEMES.getOrDefault(theme.html(), HTML_THEMES.get("default"));
            }
        } else {
            css = HTML_THEMES.getOrDefault(theme.html(), HTML_THEMES.get("default"));
            System.out.println("🎨 Using theme: " + theme.html());
        }
        
        // On stocke le CSS dans une variable statique pour l'injection post-rendu
        currentThemeCss = css;
        
        // On stocke la configuration de footer pour l'injection post-rendu
        currentFooterConfig = theme.footer();
        
        return builder.build();
    }
    
    public static Attributes buildPdfAttributes(ThemeConfig theme) {
        AttributesBuilder builder = AttributesBuilder.attributes();

        // Priorité au thème PDF custom si fourni
        if (theme.customPdfThemePath() != null) {
            try {
                Path customThemePath = Path.of(theme.customPdfThemePath());
                if (Files.exists(customThemePath)) {
                    builder.attribute("pdf-theme", theme.customPdfThemePath());
                    System.out.println("🎨 Using custom PDF theme: " + theme.customPdfThemePath());
                } else {
                    System.err.println("⚠️  Custom PDF theme not found: " + theme.customPdfThemePath());
                    fallbackToBuiltinPdfTheme(builder, theme.pdf());
                }
            } catch (Exception e) {
                System.err.println("⚠️  Cannot access custom PDF theme: " + e.getMessage());
                fallbackToBuiltinPdfTheme(builder, theme.pdf());
            }
        } else {
            fallbackToBuiltinPdfTheme(builder, theme.pdf());
        }

        // Attributs communs pour une meilleure mise en page
        builder.attribute("title-page", "");
        builder.attribute("toc", "");
        
        // Configuration du footer PDF si défini
        if (theme.footer() != null && theme.footer().hasPdfFooter()) {
            configurePdfFooter(builder, theme.footer());
        }

        return builder.build();
    }
    
    private static void configurePdfFooter(AttributesBuilder builder, FooterConfig footerConfig) {
        String footerText = footerConfig.pdfText();
        String alignment = footerConfig.pdfAlignment();
        
        // Configuration de base du footer
        builder.attribute("pdf-footer-center", "");
        builder.attribute("pdf-footer-left", "");
        builder.attribute("pdf-footer-right", "");
        
        // Configuration selon l'alignement
        switch (alignment) {
            case "left" -> builder.attribute("pdf-footer-left", footerText);
            case "right" -> builder.attribute("pdf-footer-right", footerText);
            default -> builder.attribute("pdf-footer-center", footerText);
        }
        
        // Style personnalisé du footer PDF (via attributs)
        if (footerConfig.pdfStyle() != null && !footerConfig.pdfStyle().isEmpty()) {
            // Les styles PDF sont généralement gérés par le thème PDF
            // On peut ajouter des attributs personnalisés que le thème pourrait utiliser
            builder.attribute("pdf-footer-style", footerConfig.pdfStyle());
        }
        
        System.out.println("🦶 Configured PDF footer: " + footerText + " (" + alignment + ")");
    }
    
    private static void fallbackToBuiltinPdfTheme(AttributesBuilder builder, String themeName) {
        java.util.Optional<String> themePathOpt = getPdfThemePath(themeName);
        if (themePathOpt.isPresent()) {
            builder.attribute("pdf-theme", themePathOpt.get());
            System.out.println("🎨 Using PDF theme: " + themePathOpt.get());
        } else {
            builder.attribute("pdf-theme", "default");
            System.out.println("🎨 Using default PDF theme");
        }
    }
    
    private static java.util.Optional<String> getPdfThemePath(String themeName) {
        String themePath = switch (themeName) {
            case "report" -> "themes/pdf/simple-report.yml";
            case "book" -> "themes/pdf/simple-book.yml";
            default -> null;
        };

        if (themePath != null) {
            try {
                if (Files.exists(Path.of(themePath))) {
                    return java.util.Optional.of(themePath);
                }
            } catch (Exception e) {
                System.err.println("⚠️  Cannot access PDF theme: " + e.getMessage());
            }
        }
        return java.util.Optional.empty();
    }
    
    public static String injectThemeCss(String html) {
        String result = html;
        
        // Injection du CSS
        if (!currentThemeCss.isEmpty()) {
            if (result.contains("</head>")) {
                result = result.replace("</head>", currentThemeCss + "\n</head>");
            } else {
                result = currentThemeCss + "\n" + result;
            }
        }
        
        // Injection du footer HTML si configuré
        if (currentFooterConfig != null && currentFooterConfig.hasHtmlFooter()) {
            result = injectHtmlFooter(result, currentFooterConfig);
        }
        
        return result;
    }
    
    private static String injectHtmlFooter(String html, FooterConfig footerConfig) {
        String footerText = processFooterVariables(footerConfig.htmlText());
        String position = footerConfig.htmlPosition();
        String alignment = footerConfig.htmlAlignment();
        String customStyle = footerConfig.htmlStyle();
        
        // Style de base pour le footer
        String footerStyle = switch (alignment) {
            case "left" -> "text-align: left;";
            case "right" -> "text-align: right;";
            default -> "text-align: center;";
        };
        
        // Ajout du style personnalisé si fourni
        if (customStyle != null && !customStyle.isEmpty()) {
            footerStyle += " " + customStyle;
        }
        
        // Style pour la position
        String positionStyle = "fixed".equals(position) || "fixed-bottom".equals(position) 
            ? "position: fixed; bottom: 0; left: 0; right: 0; z-index: 1000; background: inherit; padding: 10px;"
            : "margin-top: 40px; padding-top: 20px;";
        
        String footerHtml = String.format(
            "<div class=\"asciiframe-footer\" style=\"%s %s\">%s</div>",
            positionStyle, footerStyle, footerText
        );
        
        // Injection du footer avant </body> ou à la fin
        if (html.contains("</body>")) {
            return html.replace("</body>", footerHtml + "\n</body>");
        } else {
            return html + "\n" + footerHtml;
        }
    }
    
    private static String processFooterVariables(String text) {
        if (text == null) return "";
        
        // Remplacement des variables de date/heure
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.time.format.DateTimeFormatter timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        
        return text
            .replace("{date}", now.format(dateFormatter))
            .replace("{time}", now.format(timeFormatter))
            .replace("{datetime}", now.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
    }
    
    public static void cleanup() {
        currentThemeCss = "";
        currentFooterConfig = null;
    }
}