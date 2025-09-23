package co.remi.asciiframe.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Base class for all integration tests.
 * Provides common functionality and setup for Testcontainers-based tests.
 */
@Testcontainers
@ExtendWith(IntegrationTestExtension.class)
public abstract class BaseIntegrationTest {
    
    protected static final Logger logger = LoggerFactory.getLogger(BaseIntegrationTest.class);
    
    // Test configuration
    protected static final String ASCIIFRAME_JAR_PATH = System.getProperty("asciiframe.jar.path", "build/libs/app-fat.jar");
    protected static final int DEFAULT_TIMEOUT_SECONDS = 120;
    protected static final int API_TIMEOUT_SECONDS = 30;
    
    // Test data paths
    protected static final String TEST_DOCS_DIR = "/tmp/test-docs";
    protected static final String TEST_OUTPUT_DIR = "/tmp/test-output";
    
    @BeforeAll
    static void setupIntegrationTests() {
        logger.info("Starting AsciiFrame integration tests");
        
        // Verify JAR exists
        Path jarPath = Paths.get(ASCIIFRAME_JAR_PATH);
        if (!Files.exists(jarPath)) {
            throw new IllegalStateException(
                String.format("AsciiFrame JAR not found at %s. Run './gradlew shadowJar' first.", ASCIIFRAME_JAR_PATH)
            );
        }
        
        logger.info("Using AsciiFrame JAR: {}", jarPath.toAbsolutePath());
        logger.info("JAR size: {} bytes", getFileSize(jarPath));
    }
    
    private static long getFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception e) {
            return -1;
        }
    }
    
    /**
     * Creates test document content for validation
     */
    protected String createTestDocument(String title) {
        return String.format("""
            = %s
            Test Author
            :toc:
            :icons: font
            
            == Introduction
            
            This is a test document for AsciiFrame integration testing.
            Generated at: %s
            
            == Features Test
            
            [source,bash]
            ----
            echo "Hello AsciiFrame!"
            ls -la
            ----
            
            == List Test
            
            . First item
            . Second item
            . Third item
            
            == Table Test
            
            |===
            |Name |Status |Result
            
            |Test 1
            |✅ Pass
            |Working
            
            |Test 2  
            |✅ Pass
            |Working
            |===
            
            == Conclusion
            
            Integration test completed successfully!
            """, title, java.time.Instant.now());
    }
    
    /**
     * Creates a test configuration file content
     */
    protected String createTestConfig() {
        return """
            entry: docs/index.adoc
            outDir: build
            formats: [html]
            
            theme:
              html: default
              
            server:
              port: 8080
              
            watch:
              enabled: false
              
            diagrams:
              engine: kroki
              url: http://localhost:8000
            """;
    }
    
    /**
     * Waits for a condition to be true with timeout
     */
    protected void waitForCondition(java.util.function.Supplier<Boolean> condition, int timeoutSeconds, String description) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (condition.get()) {
                return;
            }
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for: " + description, e);
            }
        }
        
        throw new RuntimeException(String.format("Timeout after %d seconds waiting for: %s", timeoutSeconds, description));
    }
    
    /**
     * Validates that a URL is accessible
     */
    protected boolean isUrlAccessible(String url) {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(10))
                .build();
                
            java.net.http.HttpResponse<String> response = client.send(request, 
                java.net.http.HttpResponse.BodyHandlers.ofString());
                
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            logger.debug("URL {} not accessible: {}", url, e.getMessage());
            return false;
        }
    }
    
    /**
     * Makes an HTTP GET request and returns the response
     */
    protected HttpResponse makeHttpRequest(String url) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create(url))
            .timeout(java.time.Duration.ofSeconds(API_TIMEOUT_SECONDS))
            .build();
            
        java.net.http.HttpResponse<String> response = client.send(request, 
            java.net.http.HttpResponse.BodyHandlers.ofString());
            
        return new HttpResponse(response.statusCode(), response.body());
    }
    
    /**
     * Makes an HTTP POST request with JSON body
     */
    protected HttpResponse makeHttpPostRequest(String url, String jsonBody) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create(url))
            .timeout(java.time.Duration.ofSeconds(API_TIMEOUT_SECONDS))
            .header("Content-Type", "application/json")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();
            
        java.net.http.HttpResponse<String> response = client.send(request, 
            java.net.http.HttpResponse.BodyHandlers.ofString());
            
        return new HttpResponse(response.statusCode(), response.body());
    }
    
    /**
     * Simple HTTP response wrapper
     */
    public static class HttpResponse {
        private final int statusCode;
        private final String body;
        
        public HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
        
        public int getStatusCode() { return statusCode; }
        public String getBody() { return body; }
        
        public boolean isSuccess() { return statusCode >= 200 && statusCode < 300; }
        public boolean isClientError() { return statusCode >= 400 && statusCode < 500; }
        public boolean isServerError() { return statusCode >= 500; }
    }
    
    /**
     * Creates a MountableFile from string content
     */
    protected MountableFile createMountableFileFromString(String content, String filename) {
        try {
            File tempFile = File.createTempFile(filename, ".tmp");
            tempFile.deleteOnExit();
            Files.write(tempFile.toPath(), content.getBytes());
            return MountableFile.forHostPath(tempFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temporary file", e);
        }
    }
}