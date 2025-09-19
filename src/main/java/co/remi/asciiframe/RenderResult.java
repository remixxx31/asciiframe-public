
package co.remi.asciiframe;

public class RenderResult {
    public final String htmlPath;
    public final String pdfPath;
    public final boolean cacheHit;

    public RenderResult(String htmlPath, String pdfPath, boolean cacheHit) {
        this.htmlPath = htmlPath;
        this.pdfPath = pdfPath;
        this.cacheHit = cacheHit;
    }
}
