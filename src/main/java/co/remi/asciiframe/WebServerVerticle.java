
package co.remi.asciiframe;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.core.http.ServerWebSocket;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WebServerVerticle extends AbstractVerticle {
    private RenderService render;
    private WatcherService watcher;
    private final Set<ServerWebSocket> sockets = ConcurrentHashMap.newKeySet();

    @Override
    public void start(Promise<Void> startPromise) {
        Config cfg = Config.load();

        // RenderService now uses lazy initialization, so no blocking needed
        this.render = new RenderService(cfg, vertx);
        this.watcher = new WatcherService(cfg, () -> broadcast("build:changed"));

        Router router = Router.router(vertx);
        router.route().handler(LoggerHandler.create());
        router.route().handler(BodyHandler.create());

        router.post("/render").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            String entry = body.getString("entry", cfg.entry());
            var formats = body.getJsonArray("formats");
            
            // Validate entry file exists
            java.nio.file.Path entryPath = java.nio.file.Paths.get(entry);
            if (!java.nio.file.Files.exists(entryPath)) {
                ctx.response().setStatusCode(404).end(new JsonObject()
                    .put("error", "File not found: " + entry)
                    .put("path", entryPath.toAbsolutePath().toString())
                    .encode());
                return;
            }
            
            JsonObject res = new JsonObject();
            // rendering is blocking, keep executeBlocking for render calls
            vertx.executeBlocking(promise2 -> {
                try {
                    RenderResult rr = render.render(new DocumentSpec(entry, formats));
                    res.put("htmlPath", rr.htmlPath);
                    res.put("pdfPath", rr.pdfPath);
                    res.put("cacheHit", rr.cacheHit);
                    res.put("success", true);
                    promise2.complete();
                } catch (Exception e) {
                    System.err.println("Render error for " + entry + ": " + e.getMessage());
                    e.printStackTrace();
                    promise2.fail(e);
                }
            }, ar2 -> {
                if (ar2.failed()) {
                    String errorMsg = ar2.cause().getMessage();
                    String errorClass = ar2.cause().getClass().getSimpleName();
                    System.err.println("Render failed: " + errorClass + " - " + errorMsg);
                    ctx.response().setStatusCode(500).end(new JsonObject()
                        .put("error", errorMsg != null ? errorMsg : "Unknown render error")
                        .put("errorType", errorClass)
                        .put("entry", entry)
                        .encode());
                } else {
                    ctx.response().putHeader("Content-Type", "application/json").end(res.encode());
                    broadcast("build:done");
                }
            });
        });

        // Serve generated files from output directory
        router.get("/preview/*").handler(StaticHandler.create()
            .setCachingEnabled(true)
            .setWebRoot(cfg.outDir())
            .setIndexPage("index.html"));
            
        // Also serve files directly from output directory
        router.get("/build/*").handler(StaticHandler.create()
            .setCachingEnabled(true) 
            .setWebRoot(cfg.outDir()));
            
        router.get("/health").handler(ctx -> ctx.end("ok"));

        vertx.createHttpServer(new HttpServerOptions().setCompressionSupported(true))
                 .requestHandler(router)
                 .webSocketHandler(ws -> {
                    if ("/events".equals(ws.path())) {
                        sockets.add(ws);
                        ws.closeHandler(v -> sockets.remove(ws));
                    } else {
                        ws.reject();
                    }
                 })
                 .listen(cfg.port(), http -> {
                    if (http.succeeded()) {
                        startPromise.complete();
                        if (cfg.watchEnabled()) watcher.start();
                    } else startPromise.fail(http.cause());
                 });
    }

    private void broadcast(String event) {
        Buffer msg = Buffer.buffer(event);
        sockets.forEach(s -> { if (!s.writeQueueFull()) s.write(msg); });
    }
}
