package co.remi.asciiframe;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.jruby.AsciidoctorJRuby;

/**
 * Worker Verticle dédiée au rendu AsciidoctorJ
 * S'exécute sur un thread pool séparé pour éviter de bloquer l'event loop
 */
public class RenderWorkerVerticle extends AbstractVerticle {
    
    private Asciidoctor asciidoctor;
    private RenderService renderService;
    
    @Override
    public void start(Promise<Void> startPromise) {
        System.out.println("🔧 RenderWorkerVerticle starting on thread: " + Thread.currentThread().getName());
        
        try {
            // Initialisation d'AsciidoctorJ dans le worker thread (non bloquant pour l'event loop)
            System.out.println("📚 Initializing AsciidoctorJ in worker thread...");
            this.asciidoctor = AsciidoctorJRuby.Factory.create();
            
            Config cfg = Config.load();
            this.renderService = new RenderService(cfg, vertx);
            
            // Écouter les messages de rendu sur l'event bus
            vertx.eventBus().consumer("render.request", this::handleRenderRequest);
            
            System.out.println("✅ RenderWorkerVerticle ready!");
            startPromise.complete();
            
        } catch (Exception e) {
            System.err.println("❌ RenderWorkerVerticle failed to start: " + e.getMessage());
            startPromise.fail(e);
        }
    }
    
    private void handleRenderRequest(Message<JsonObject> message) {
        System.out.println("🎯 Processing render request on thread: " + Thread.currentThread().getName());
        
        try {
            JsonObject request = message.body();
            String entry = request.getString("entry");
            var formats = request.getJsonArray("formats");
            
            // Le rendu se fait dans le worker thread, pas d'event loop bloqué
            RenderResult result = renderService.render(new DocumentSpec(entry, formats));
            
            JsonObject response = new JsonObject()
                .put("htmlPath", result.htmlPath)
                .put("pdfPath", result.pdfPath)
                .put("cacheHit", result.cacheHit)
                .put("success", true);
                
            message.reply(response);
            System.out.println("✅ Render completed successfully");
            
        } catch (Exception e) {
            System.err.println("❌ Render failed: " + e.getMessage());
            message.reply(new JsonObject().put("success", false).put("error", e.getMessage()));
        }
    }
}