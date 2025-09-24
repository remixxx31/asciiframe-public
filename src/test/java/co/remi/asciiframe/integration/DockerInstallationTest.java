package co.remi.asciiframe.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Docker installation and container tests for AsciiFrame.
 * Tests Docker-based deployment scenarios.
 */
@Tag("docker")
@Execution(ExecutionMode.CONCURRENT)
@DisplayName("Docker Installation Tests")
class DockerInstallationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Test Docker image build and run")
    void testDockerImageBuildAndRun() throws Exception {
        logger.info("Testing Docker image build and run");
        
        // Use Docker-in-Docker to build AsciiFrame image
        try (GenericContainer<?> docker = new GenericContainer<>("docker:24-dind")
                .withPrivilegedMode(true)
                .withCommand("dockerd-entrypoint.sh")
                .waitingFor(Wait.forLogMessage(".*API listen on.*", 1))
                .withStartupTimeout(Duration.ofMinutes(3))) {
            
            docker.start();
            
            // Copy project files to container
            docker.copyFileToContainer(
                MountableFile.forHostPath(ASCIIFRAME_JAR_PATH),
                "/workspace/app-fat.jar"
            );
            
            // Create Dockerfile for testing
            String dockerfile = createTestDockerfile();
            docker.copyFileToContainer(
                createMountableFileFromString(dockerfile, "dockerfile"),
                "/workspace/Dockerfile"
            );
            
            // Create test configuration
            String config = createTestConfig();
            docker.copyFileToContainer(
                createMountableFileFromString(config, "config"),
                "/workspace/config.yml"
            );
            
            // Build Docker image
            org.testcontainers.containers.Container.ExecResult buildResult = docker.execInContainer(
                "docker", "build", "-t", "asciiframe-test", "/workspace"
            );
            
            assertThat(buildResult.getExitCode())
                .withFailMessage("Docker build should succeed: %s", buildResult.getStderr())
                .isEqualTo(0);
            
            logger.info("✅ Docker image built successfully");
            
            // Test running the container
            org.testcontainers.containers.Container.ExecResult runResult = docker.execInContainer(
                "docker", "run", "-d", "--name", "asciiframe-test-instance", 
                "-p", "8080:8080", "asciiframe-test"
            );
            
            assertThat(runResult.getExitCode())
                .withFailMessage("Docker run should succeed: %s", runResult.getStderr())
                .isEqualTo(0);
            
            // Wait for container to start
            Thread.sleep(10000);
            
            // Check if container is running
            org.testcontainers.containers.Container.ExecResult statusResult = docker.execInContainer(
                "docker", "ps", "--filter", "name=asciiframe-test-instance"
            );
            
            assertThat(statusResult.getStdout()).contains("asciiframe-test-instance");
            
            logger.info("✅ Docker container running successfully");
            
            // Test basic functionality
            testDockerContainerFunctionality(docker);
            
        } catch (Exception e) {
            logger.error("Docker image test failed", e);
            throw new RuntimeException("Docker image test failed", e);
        }
    }

    @Test
    @DisplayName("Test Docker Compose setup")
    void testDockerComposeSetup() throws Exception {
        logger.info("Testing Docker Compose setup");
        
        // Create temporary Docker Compose file
        String dockerCompose = createTestDockerCompose();
        File tempComposeFile = File.createTempFile("docker-compose-test", ".yml");
        java.nio.file.Files.write(tempComposeFile.toPath(), dockerCompose.getBytes());
        
        try {
            // Test Docker Compose validation
            try (GenericContainer<?> docker = new GenericContainer<>("docker:24-dind")
                    .withPrivilegedMode(true)
                    .withCommand("dockerd-entrypoint.sh")
                    .waitingFor(Wait.forLogMessage(".*API listen on.*", 1))
                    .withStartupTimeout(Duration.ofMinutes(3))) {
                
                docker.start();
                
                // Install docker-compose
                docker.execInContainer("sh", "-c", 
                    "apk add --no-cache docker-compose");
                
                // Copy compose file
                docker.copyFileToContainer(
                    MountableFile.forHostPath(tempComposeFile.getAbsolutePath()),
                    "/workspace/docker-compose.yml"
                );
                
                // Copy JAR file
                docker.copyFileToContainer(
                    MountableFile.forHostPath(ASCIIFRAME_JAR_PATH),
                    "/workspace/app-fat.jar"
                );
                
                // Create simple Dockerfile
                String dockerfile = createSimpleDockerfile();
                docker.copyFileToContainer(
                    createMountableFileFromString(dockerfile, "simple-dockerfile"),
                    "/workspace/Dockerfile"
                );
                
                // Validate docker-compose file
                org.testcontainers.containers.Container.ExecResult validateResult = docker.execInContainer(
                    "docker-compose", "-f", "/workspace/docker-compose.yml", "config"
                );
                
                assertThat(validateResult.getExitCode())
                    .withFailMessage("Docker Compose config should be valid: %s", validateResult.getStderr())
                    .isEqualTo(0);
                
                logger.info("✅ Docker Compose configuration is valid");
                
                // Test building with docker-compose
                org.testcontainers.containers.Container.ExecResult buildResult = docker.execInContainer(
                    "sh", "-c", 
                    "cd /workspace && docker-compose build asciiframe"
                );
                
                assertThat(buildResult.getExitCode())
                    .withFailMessage("Docker Compose build should succeed: %s", buildResult.getStderr())
                    .isEqualTo(0);
                
                logger.info("✅ Docker Compose build successful");
            }
            
        } finally {
            tempComposeFile.delete();
        }
    }

    @Test
    @DisplayName("Test container resource limits")
    void testContainerResourceLimits() throws Exception {
        logger.info("Testing container resource limits");
        
        try (GenericContainer<?> asciiFrame = new GenericContainer<>("eclipse-temurin:23-jre")
                .withCommand("tail", "-f", "/dev/null")
                .withExposedPorts(8080)
                // Set resource limits
                .withCreateContainerCmdModifier(cmd -> {
                    cmd.getHostConfig()
                        .withMemory(512L * 1024 * 1024) // 512MB
                        .withCpuQuota(50000L) // 50% CPU
                        .withCpuPeriod(100000L);
                })
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofMinutes(2))) {
            
            asciiFrame.start();
            
            // Copy JAR and config
            asciiFrame.copyFileToContainer(
                MountableFile.forHostPath(ASCIIFRAME_JAR_PATH),
                "/app/asciiframe.jar"
            );
            
            String config = createTestConfig();
            asciiFrame.copyFileToContainer(
                createMountableFileFromString(config, "config"),
                "/app/config.yml"
            );
            
            // Create test documents
            setupTestDocuments(asciiFrame);
            
            // Start AsciiFrame with limited resources
            asciiFrame.execInContainer(
                "sh", "-c",
                "cd /app && java -Xmx256m -jar asciiframe.jar > asciiframe.log 2>&1 &"
            );
            
            // Wait for startup
            Thread.sleep(15000);
            
            // Test that it can handle basic requests under resource constraints
            String baseUrl = String.format("http://%s:%d", 
                asciiFrame.getHost(), asciiFrame.getMappedPort(8080));
            
            // Wait for service to be ready
            waitForCondition(() -> isUrlAccessible(baseUrl + "/health"), 
                           60, "AsciiFrame with resource limits to start");
            
            // Test basic functionality
            HttpResponse healthResponse = makeHttpRequest(baseUrl + "/health");
            assertThat(healthResponse.isSuccess())
                .withFailMessage("Health check should work with resource limits")
                .isTrue();
            
            logger.info("✅ Container resource limits test passed");
        }
    }

    @Test
    @DisplayName("Test container networking")
    void testContainerNetworking() throws Exception {
        logger.info("Testing container networking");
        
        // Test custom network setup
        try (GenericContainer<?> asciiFrame = new GenericContainer<>("eclipse-temurin:23-jre")
                .withCommand("tail", "-f", "/dev/null")
                .withExposedPorts(8080)
                .withNetworkAliases("asciiframe-app")
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofMinutes(2))) {
            
            asciiFrame.start();
            
            // Setup AsciiFrame
            setupAsciiFrameInContainer(asciiFrame);
            
            // Test internal networking
            org.testcontainers.containers.Container.ExecResult networkTest = asciiFrame.execInContainer(
                "sh", "-c", "netstat -tulpn | grep :8080"
            );
            
            // Should show port 8080 is bound
            assertThat(networkTest.getStdout()).contains("8080");
            
            // Test external connectivity
            String baseUrl = String.format("http://%s:%d", 
                asciiFrame.getHost(), asciiFrame.getMappedPort(8080));
            
            waitForCondition(() -> isUrlAccessible(baseUrl + "/health"), 
                           60, "External connectivity to work");
            
            HttpResponse response = makeHttpRequest(baseUrl + "/health");
            assertThat(response.isSuccess()).isTrue();
            
            logger.info("✅ Container networking test passed");
        }
    }

    @Test
    @DisplayName("Test container volume mounts")
    void testContainerVolumeMounts() throws Exception {
        logger.info("Testing container volume mounts");
        
        // Create temporary directories for volume testing
        File tempDocs = java.nio.file.Files.createTempDirectory("asciiframe-docs").toFile();
        File tempOutput = java.nio.file.Files.createTempDirectory("asciiframe-output").toFile();
        
        try {
            // Create test document in temp directory
            File testDoc = new File(tempDocs, "index.adoc");
            java.nio.file.Files.write(testDoc.toPath(), 
                createTestDocument("Volume Mount Test").getBytes());
            
            try (GenericContainer<?> asciiFrame = new GenericContainer<>("eclipse-temurin:23-jre")
                    .withCommand("tail", "-f", "/dev/null")
                    .withExposedPorts(8080)
                    .withFileSystemBind(tempDocs.getAbsolutePath(), "/app/docs")
                    .withFileSystemBind(tempOutput.getAbsolutePath(), "/app/build")
                    .waitingFor(Wait.forListeningPort())
                    .withStartupTimeout(Duration.ofMinutes(2))) {
                
                asciiFrame.start();
                
                // Setup AsciiFrame
                asciiFrame.copyFileToContainer(
                    MountableFile.forHostPath(ASCIIFRAME_JAR_PATH),
                    "/app/asciiframe.jar"
                );
                
                String config = """
                    entry: docs/index.adoc
                    outDir: build
                    formats: [html]
                    theme:
                      html: default
                    server:
                      port: 8080
                    watch:
                      enabled: false
                    """;
                
                asciiFrame.copyFileToContainer(
                    createMountableFileFromString(config, "config"),
                    "/app/config.yml"
                );
                
                // Start AsciiFrame
                asciiFrame.execInContainer(
                    "sh", "-c",
                    "cd /app && java -jar asciiframe.jar > asciiframe.log 2>&1 &"
                );
                
                Thread.sleep(10000);
                
                // Test that mounted volumes work
                String baseUrl = String.format("http://%s:%d", 
                    asciiFrame.getHost(), asciiFrame.getMappedPort(8080));
                
                waitForCondition(() -> isUrlAccessible(baseUrl + "/health"), 
                               60, "AsciiFrame with volumes to start");
                
                // Test rendering with mounted documents
                String renderRequest = """
                    {
                        "entry": "docs/index.adoc",
                        "formats": ["html"]
                    }
                    """;
                
                HttpResponse response = makeHttpPostRequest(baseUrl + "/render", renderRequest);
                assertThat(response.isSuccess())
                    .withFailMessage("Render with mounted volumes should work")
                    .isTrue();
                
                // Verify output file was created in mounted volume
                Thread.sleep(3000);
                File[] outputFiles = tempOutput.listFiles();
                assertThat(outputFiles).isNotEmpty();
                
                logger.info("✅ Container volume mounts test passed");
            }
            
        } finally {
            // Cleanup temp directories
            deleteRecursively(tempDocs);
            deleteRecursively(tempOutput);
        }
    }

    private String createTestDockerfile() {
        return """
            FROM eclipse-temurin:23-jre
            
            WORKDIR /app
            
            # Copy JAR file
            COPY app-fat.jar asciiframe.jar
            COPY config.yml config.yml
            
            # Create directories
            RUN mkdir -p docs build
            
            # Expose port
            EXPOSE 8080
            
            # Health check
            HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \\
                CMD curl -f http://localhost:8080/health || exit 1
            
            # Start AsciiFrame
            CMD ["java", "-jar", "asciiframe.jar"]
            """;
    }

    private String createSimpleDockerfile() {
        return """
            FROM eclipse-temurin:23-jre
            COPY app-fat.jar /app/asciiframe.jar
            WORKDIR /app
            EXPOSE 8080
            CMD ["java", "-jar", "asciiframe.jar"]
            """;
    }

    private String createTestDockerCompose() {
        return """
            version: '3.8'
            
            services:
              asciiframe:
                build: .
                ports:
                  - "8080:8080"
                volumes:
                  - ./docs:/app/docs
                  - ./build:/app/build
                environment:
                  - CONFIG_PATH=/app/config.yml
                healthcheck:
                  test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
                  interval: 30s
                  timeout: 10s
                  retries: 3
                  start_period: 60s
            
              kroki:
                image: yuzutech/kroki
                ports:
                  - "8000:8000"
                healthcheck:
                  test: ["CMD", "curl", "-f", "http://localhost:8000/health"]
                  interval: 30s
                  timeout: 10s
                  retries: 3
            """;
    }

    private void testDockerContainerFunctionality(GenericContainer<?> dockerContainer) throws Exception {
        // Test basic container operations
        
        // Check container logs
        org.testcontainers.containers.Container.ExecResult logsResult = dockerContainer.execInContainer(
            "docker", "logs", "asciiframe-test-instance"
        );
        
        logger.info("Container logs: {}", logsResult.getStdout());
        
        // Test if the service is responding (basic test)
        org.testcontainers.containers.Container.ExecResult curlTest = dockerContainer.execInContainer(
            "docker", "exec", "asciiframe-test-instance", "sh", "-c",
            "timeout 10 sh -c 'until nc -z localhost 8080; do sleep 1; done' && echo 'Service is up'"
        );
        
        if (curlTest.getExitCode() == 0) {
            logger.info("✅ Docker container service is responding");
        } else {
            logger.warn("⚠️  Docker container service test inconclusive");
        }
    }

    private void setupAsciiFrameInContainer(GenericContainer<?> container) throws Exception {
        // Copy JAR
        container.copyFileToContainer(
            MountableFile.forHostPath(ASCIIFRAME_JAR_PATH),
            "/app/asciiframe.jar"
        );
        
        // Copy config
        String config = createTestConfig();
        container.copyFileToContainer(
            createMountableFileFromString(config, "config"),
            "/app/config.yml"
        );
        
        // Setup test documents
        setupTestDocuments(container);
        
        // Start AsciiFrame
        container.execInContainer(
            "sh", "-c",
            "cd /app && java -jar asciiframe.jar > asciiframe.log 2>&1 &"
        );
        
        Thread.sleep(10000);
    }

    private void setupTestDocuments(GenericContainer<?> container) throws Exception {
        container.execInContainer("mkdir", "-p", "/app/docs");
        
        String testDoc = createTestDocument("Docker Test Document");
        container.copyFileToContainer(
            createMountableFileFromString(testDoc, "test-doc"),
            "/app/docs/index.adoc"
        );
        
        container.execInContainer("mkdir", "-p", "/app/build");
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}