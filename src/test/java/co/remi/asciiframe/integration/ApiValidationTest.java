package co.remi.asciiframe.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * API validation tests for AsciiFrame.
 * Tests all REST endpoints and functionality in a clean container environment.
 */
@Tag("integration")
@Execution(ExecutionMode.CONCURRENT)
@DisplayName("API Validation Tests")
class ApiValidationTest extends BaseIntegrationTest {

    private GenericContainer<?> asciiFrameContainer;
    private String baseUrl;

    @BeforeEach
    void setupContainer() {
        logger.info("Setting up AsciiFrame container for API testing");
        
        // Create container with Java and AsciiFrame - use lightweight stable JRE  
        asciiFrameContainer = new GenericContainer<>(DockerImageName.parse("openjdk:21-jre-slim"))
            .withCommand("tail", "-f", "/dev/null")
            .withExposedPorts(8080)
            .withWorkingDirectory("/app")
            .waitingFor(Wait.forLogMessage(".*", 1).withStartupTimeout(Duration.ofSeconds(30)));
        
        // Start container
        asciiFrameContainer.start();
        
        try {
            // Copy JAR to container
            asciiFrameContainer.copyFileToContainer(
                MountableFile.forHostPath(ASCIIFRAME_JAR_PATH),
                "/app/asciiframe.jar"
            );
            
            // Create test configuration
            String config = createTestConfig();
            asciiFrameContainer.copyFileToContainer(
                createMountableFileFromString(config, "config"),
                "/app/config.yml"
            );
            
            // Create test documents
            setupTestDocuments();
            
            // Start AsciiFrame in background
            startAsciiFrame();
            
            // Setup base URL
            baseUrl = String.format("http://%s:%d", 
                asciiFrameContainer.getHost(), 
                asciiFrameContainer.getMappedPort(8080));
            
            // Wait for service to be ready
            waitForServiceReady();
            
            logger.info("AsciiFrame container ready at: {}", baseUrl);
            
        } catch (Exception e) {
            asciiFrameContainer.stop();
            throw new RuntimeException("Failed to setup AsciiFrame container", e);
        }
    }

    @AfterEach
    void cleanupContainer() {
        if (asciiFrameContainer != null) {
            try {
                // Get logs for debugging if needed
                String logs = asciiFrameContainer.getLogs();
                logger.debug("AsciiFrame container logs:\n{}", logs);
            } catch (Exception e) {
                logger.warn("Failed to get container logs", e);
            }
            
            asciiFrameContainer.stop();
        }
    }

    @Test
    @DisplayName("Test health endpoint")
    void testHealthEndpoint() throws Exception {
        logger.info("Testing health endpoint");
        
        HttpResponse response = makeHttpRequest(baseUrl + "/health");
        
        assertThat(response.isSuccess())
            .withFailMessage("Health endpoint should return success, got: %d - %s", 
                response.getStatusCode(), response.getBody())
            .isTrue();
        
        // Verify response content
        String body = response.getBody();
        assertThat(body).isNotEmpty();
        
        // Should contain status information
        assertThat(body.toLowerCase()).containsAnyOf("ok", "healthy", "up", "running");
        
        logger.info("✅ Health endpoint test passed");
    }

    @Test
    @DisplayName("Test render endpoint with valid document")
    void testRenderEndpoint() throws Exception {
        logger.info("Testing render endpoint");
        
        // Prepare render request
        String renderRequest = """
            {
                "entry": "docs/index.adoc",
                "formats": ["html"]
            }
            """;
        
        HttpResponse response = makeHttpPostRequest(baseUrl + "/render", renderRequest);
        
        assertThat(response.isSuccess())
            .withFailMessage("Render should succeed, got: %d - %s", 
                response.getStatusCode(), response.getBody())
            .isTrue();
        
        // Verify response contains expected elements
        String body = response.getBody();
        assertThat(body).isNotEmpty();
        
        logger.info("✅ Render endpoint test passed");
    }

    @Test
    @DisplayName("Test render endpoint with invalid document")
    void testRenderEndpointWithInvalidDocument() throws Exception {
        logger.info("Testing render endpoint with invalid document");
        
        String renderRequest = """
            {
                "entry": "docs/nonexistent.adoc",
                "formats": ["html"]
            }
            """;
        
        HttpResponse response = makeHttpPostRequest(baseUrl + "/render", renderRequest);
        
        // Should return an error status
        assertThat(response.isClientError() || response.isServerError())
            .withFailMessage("Invalid document should return error, got: %d", response.getStatusCode())
            .isTrue();
        
        logger.info("✅ Invalid document handling test passed");
    }

    @Test
    @DisplayName("Test render endpoint with malformed JSON")
    void testRenderEndpointWithMalformedJson() throws Exception {
        logger.info("Testing render endpoint with malformed JSON");
        
        String malformedJson = "{ invalid json }";
        
        HttpResponse response = makeHttpPostRequest(baseUrl + "/render", malformedJson);
        
        assertThat(response.isClientError())
            .withFailMessage("Malformed JSON should return client error, got: %d", response.getStatusCode())
            .isTrue();
        
        logger.info("✅ Malformed JSON handling test passed");
    }

    @Test
    @DisplayName("Test preview endpoint")
    void testPreviewEndpoint() throws Exception {
        logger.info("Testing preview endpoint");
        
        // First render a document to ensure it exists
        String renderRequest = """
            {
                "entry": "docs/index.adoc",
                "formats": ["html"]
            }
            """;
        makeHttpPostRequest(baseUrl + "/render", renderRequest);
        
        // Wait a moment for rendering to complete
        Thread.sleep(2000);
        
        // Test preview endpoint
        HttpResponse response = makeHttpRequest(baseUrl + "/preview/index.html");
        
        assertThat(response.isSuccess())
            .withFailMessage("Preview should succeed, got: %d - %s", 
                response.getStatusCode(), response.getBody())
            .isTrue();
        
        // Verify HTML content
        String html = response.getBody();
        assertThat(html).contains("<html").contains("</html>");
        assertThat(html).contains("Test Document");
        
        logger.info("✅ Preview endpoint test passed");
    }

    @Test
    @DisplayName("Test multiple formats rendering")
    void testMultipleFormatsRendering() throws Exception {
        logger.info("Testing multiple formats rendering");
        
        String renderRequest = """
            {
                "entry": "docs/index.adoc",
                "formats": ["html", "pdf"]
            }
            """;
        
        HttpResponse response = makeHttpPostRequest(baseUrl + "/render", renderRequest);
        
        assertThat(response.isSuccess())
            .withFailMessage("Multi-format render should succeed, got: %d - %s", 
                response.getStatusCode(), response.getBody())
            .isTrue();
        
        logger.info("✅ Multiple formats rendering test passed");
    }

    @Test
    @DisplayName("Test concurrent rendering requests")
    void testConcurrentRendering() throws Exception {
        logger.info("Testing concurrent rendering requests");
        
        String renderRequest = """
            {
                "entry": "docs/index.adoc",
                "formats": ["html"]
            }
            """;
        
        // Submit multiple concurrent requests
        java.util.List<java.util.concurrent.CompletableFuture<HttpResponse>> futures = new java.util.ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            futures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    return makeHttpPostRequest(baseUrl + "/render", renderRequest);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }
        
        // Wait for all requests to complete
        java.util.List<HttpResponse> responses = futures.stream()
            .map(java.util.concurrent.CompletableFuture::join)
            .toList();
        
        // Verify all requests succeeded
        for (int i = 0; i < responses.size(); i++) {
            HttpResponse response = responses.get(i);
            assertThat(response.isSuccess())
                .withFailMessage("Concurrent request %d should succeed, got: %d", i, response.getStatusCode())
                .isTrue();
        }
        
        logger.info("✅ Concurrent rendering test passed");
    }

    @Test
    @DisplayName("Test different theme configurations")
    void testThemeConfigurations() throws Exception {
        logger.info("Testing different theme configurations");
        
        // Test different themes
        String[] themes = {"default", "dark", "minimal"};
        
        for (String theme : themes) {
            logger.info("Testing theme: {}", theme);
            
            // Update configuration with new theme
            updateContainerConfig(theme);
            
            // Restart AsciiFrame with new config
            restartAsciiFrame();
            
            // Test rendering with new theme
            String renderRequest = String.format("""
                {
                    "entry": "docs/index.adoc",
                    "formats": ["html"]
                }
                """);
            
            HttpResponse response = makeHttpPostRequest(baseUrl + "/render", renderRequest);
            
            assertThat(response.isSuccess())
                .withFailMessage("Theme %s should work, got: %d - %s", 
                    theme, response.getStatusCode(), response.getBody())
                .isTrue();
        }
        
        logger.info("✅ Theme configurations test passed");
    }

    @Test
    @DisplayName("Test error handling and recovery")
    void testErrorHandlingAndRecovery() throws Exception {
        logger.info("Testing error handling and recovery");
        
        // Test with invalid configuration
        String badRequest = """
            {
                "entry": "",
                "formats": []
            }
            """;
        
        HttpResponse badResponse = makeHttpPostRequest(baseUrl + "/render", badRequest);
        assertThat(badResponse.isClientError()).isTrue();
        
        // Verify service is still responsive after error
        Thread.sleep(1000);
        
        HttpResponse healthResponse = makeHttpRequest(baseUrl + "/health");
        assertThat(healthResponse.isSuccess())
            .withFailMessage("Service should remain healthy after error")
            .isTrue();
        
        // Test valid request still works
        String goodRequest = """
            {
                "entry": "docs/index.adoc",
                "formats": ["html"]
            }
            """;
        
        HttpResponse goodResponse = makeHttpPostRequest(baseUrl + "/render", goodRequest);
        assertThat(goodResponse.isSuccess())
            .withFailMessage("Valid request should work after error recovery")
            .isTrue();
        
        logger.info("✅ Error handling and recovery test passed");
    }

    private void setupTestDocuments() throws Exception {
        // Create test document directory
        asciiFrameContainer.execInContainer("mkdir", "-p", "/app/docs");
        
        // Create test document
        String testDoc = createTestDocument("Test Document");
        asciiFrameContainer.copyFileToContainer(
            createMountableFileFromString(testDoc, "test-doc"),
            "/app/docs/index.adoc"
        );
        
        // Create output directory
        asciiFrameContainer.execInContainer("mkdir", "-p", "/app/build");
    }

    private void startAsciiFrame() throws Exception {
        // Start AsciiFrame in background with better error handling
        org.testcontainers.containers.Container.ExecResult result = asciiFrameContainer.execInContainer(
            "sh", "-c", 
            "cd /app && java -Xmx256m -jar asciiframe.jar > asciiframe.log 2>&1 &"
        );
        
        // Give the service a moment to start
        Thread.sleep(2000);
        
        // Check if the process started successfully
        org.testcontainers.containers.Container.ExecResult checkResult = asciiFrameContainer.execInContainer(
            "sh", "-c", "ps aux | grep asciiframe.jar | grep -v grep"
        );
        
        if (checkResult.getExitCode() != 0) {
            // Get logs for debugging
            org.testcontainers.containers.Container.ExecResult logResult = asciiFrameContainer.execInContainer(
                "cat", "/app/asciiframe.log"
            );
            throw new RuntimeException("Failed to start AsciiFrame. Logs: " + logResult.getStdout());
        }
    }

    private void waitForServiceReady() {
        logger.info("Waiting for AsciiFrame service to be ready...");
        
        waitForCondition(() -> isUrlAccessible(baseUrl + "/health"), 
                        60, "AsciiFrame service to start");
        
        logger.info("AsciiFrame service is ready!");
    }

    private void updateContainerConfig(String theme) throws Exception {
        String newConfig = String.format("""
            entry: docs/index.adoc
            outDir: build
            formats: [html]
            
            theme:
              html: %s
              
            server:
              port: 8080
              
            watch:
              enabled: false
            """, theme);
        
        asciiFrameContainer.copyFileToContainer(
            createMountableFileFromString(newConfig, "new-config"),
            "/app/config.yml"
        );
    }

    private void restartAsciiFrame() throws Exception {
        // Stop current instance
        try {
            asciiFrameContainer.execInContainer("pkill", "-f", "asciiframe.jar");
            Thread.sleep(3000);
        } catch (Exception e) {
            // Ignore if no process to kill
        }
        
        // Start new instance
        startAsciiFrame();
        
        // Wait for service to be ready
        Thread.sleep(5000);
        waitForServiceReady();
    }
}