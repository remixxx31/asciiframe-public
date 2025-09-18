
package co.remi.asciiframe;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.asciidoctor.jruby.AsciidoctorJRuby;
import org.asciidoctor.Attributes;
import com.github.benmanes.caffeine.cache.*;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

public class RenderService {
    private final Config cfg;
    private volatile Asciidoctor adoc;
    private final Cache<String, String> memo; // docHash -> html path
    private volatile WebClient http;
    private final Vertx vertx;

    public RenderService(Config cfg, Vertx vertx) {
        this.cfg = cfg;
        this.vertx = vertx;
        // Lazy initialization - don't create AsciidoctorJ until first render
        this.adoc = null;
        this.memo = Caffeine.newBuilder().maximumSize(128).build();
        // Lazy initialization for WebClient too
        this.http = null;
    }

    private Asciidoctor getAsciidoctor() {
        if (adoc == null) {
            synchronized (this) {
                if (adoc == null) {
                    adoc = AsciidoctorJRuby.Factory.create();
                }
            }
        }
        return adoc;
    }

    private WebClient getWebClient() {
        if (http == null) {
            synchronized (this) {
                if (http == null) {
                    http = WebClient.create(vertx, new WebClientOptions().setTrustAll(true));
                }
            }
        }
        return http;
    }

    public RenderResult render(DocumentSpec spec) throws Exception {
        String docText = Files.readString(Path.of(spec.entry));
        String docHash = sha256(docText + String.join(",", spec.formats));

        String outHtml = Path.of(cfg.outDir(), "index.html").toString();
        String outPdf = Path.of(cfg.outDir(), "index.pdf").toString();

        boolean cacheHit = false;
        String maybe = memo.getIfPresent(docHash);
        if (maybe != null && Files.exists(Path.of(outHtml))) {
            cacheHit = true;
            return new RenderResult(outHtml, spec.formats.contains("pdf") ? outPdf : null, true);
        }

        Options options = Options.builder()
                .safe(SafeMode.SERVER)
                .baseDir(Path.of(".").toFile())
                .toFile(false)
                .build();

        Attributes attrs = ThemeManager.buildHtmlAttributes(cfg.theme());
        options.setAttributes(attrs);

        String html = getAsciidoctor().convert(docText, options);
        html = processKrokiBlocks(html);
        html = ThemeManager.injectThemeCss(html);

        AtomicFiles.writeString(Path.of(outHtml), html);

        if (spec.formats.contains("pdf")) {
            Attributes pdfAttrs = ThemeManager.buildPdfAttributes(cfg.theme());
            
            Options pdfOpts = Options.builder()
                    .safe(SafeMode.SERVER)
                    .baseDir(Path.of(".").toFile())
                    .toFile(Path.of(outPdf).toFile())
                    .backend("pdf")
                    .attributes(pdfAttrs)
                    .build();
            getAsciidoctor().convert(docText, pdfOpts);
        }
        
        ThemeManager.cleanup();

        memo.put(docHash, outHtml);
        return new RenderResult(outHtml, spec.formats.contains("pdf") ? outPdf : null, cacheHit);
    }

    private String processKrokiBlocks(String html) throws Exception {
        // Replace <pre> blocks heuristically; a robust impl would hook Asciidoctor extensions.
        Pattern p = Pattern.compile("<pre[^>]*>([\\s\\S]*?)</pre>");
        Matcher m = p.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String code = m.group(1);
            boolean isMermaid = code.contains("graph TD") || code.contains("flowchart") || code.contains("sequenceDiagram");
            boolean isPuml = code.contains("@startuml");
            if (isMermaid || isPuml) {
                String type = isPuml ? "plantuml" : "mermaid";
                byte[] svg = renderDiagramCached(type, code);
                String img = "<div class=\"diagram\">" + new String(svg, StandardCharsets.UTF_8) + "</div>";
                m.appendReplacement(sb, Matcher.quoteReplacement(img));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private byte[] renderDiagramCached(String type, String source) throws Exception {
        String key = sha256(type + "\n" + source);
        Path cacheDir = Path.of(cfg.diagramsCacheDir());
        Files.createDirectories(cacheDir);
        Path svgPath = cacheDir.resolve(key + ".svg");
        if (Files.exists(svgPath)) {
            return Files.readAllBytes(svgPath);
        }
        byte[] svg = renderKroki(type, source);
        AtomicFiles.writeBytes(svgPath, svg);
        return svg;
    }

    private byte[] renderKroki(String type, String source) throws Exception {
        var req = getWebClient().postAbs(cfg.krokiUrl() + "/" + type + "/svg");
        var promise = new java.util.concurrent.CompletableFuture<byte[]>();
        req.putHeader("Content-Type","text/plain; charset=utf-8")
           .sendBuffer(Buffer.buffer(source, "UTF-8"), ar -> {
                if (ar.succeeded()) promise.complete(ar.result().body().getBytes());
                else promise.completeExceptionally(ar.cause());
           });
        return promise.get(20, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static String sha256(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
