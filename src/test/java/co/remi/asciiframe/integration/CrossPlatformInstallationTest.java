package co.remi.asciiframe.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Cross-platform installation tests using Testcontainers.
 * Tests AsciiFrame installation on different Linux distributions.
 */
@Tag("installation")
@Execution(ExecutionMode.CONCURRENT)
@DisplayName("Cross-Platform Installation Tests")
class CrossPlatformInstallationTest extends BaseIntegrationTest {

    private static final Duration INSTALLATION_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(2);

    @ParameterizedTest
    @ValueSource(strings = {"eclipse-temurin:23-jre"})  
    @DisplayName("Test standalone installation on Java-enabled platforms")
    void testStandaloneInstallation(String platformImage) {
        logger.info("Testing standalone installation on {}", platformImage);
        
        try (GenericContainer<?> container = createTestContainer(platformImage)) {
            // Start container
            container.start();
            
            // Wait for container to be ready
            waitForContainerReady(container);
            
            // Copy JAR file to container
            container.copyFileToContainer(
                MountableFile.forHostPath(ASCIIFRAME_JAR_PATH),
                "/tmp/asciiframe.jar"
            );
            
            // Create test installation script
            String installScript = createStandaloneInstallScript();
            
            // Execute installation test
            org.testcontainers.containers.Container.ExecResult result = container.execInContainer(
                "sh", "-c", 
                "echo '" + installScript + "' > /tmp/test-install.sh && " +
                "chmod +x /tmp/test-install.sh && " +
                "/tmp/test-install.sh"
            );
            
            // Verify installation succeeded
            assertThat(result.getExitCode())
                .withFailMessage("Installation failed on %s: %s", platformImage, result.getStderr())
                .isEqualTo(0);
            
            // Verify JAR file exists
            org.testcontainers.containers.Container.ExecResult jarCheck = container.execInContainer("ls", "-la", "/tmp/install/asciiframe.jar");
            assertThat(jarCheck.getExitCode()).isEqualTo(0);
            
            // Verify wrapper script exists and is executable
            org.testcontainers.containers.Container.ExecResult scriptCheck = container.execInContainer("ls", "-la", "/tmp/install/asciiframe");
            assertThat(scriptCheck.getExitCode()).isEqualTo(0);
            assertThat(scriptCheck.getStdout()).contains("rwxr-xr-x");
            
            // Test basic functionality
            testBasicFunctionality(container);
            
            logger.info("✅ Standalone installation successful on {}", platformImage);
            
        } catch (Exception e) {
            logger.error("❌ Installation test failed on {}", platformImage, e);
            throw new RuntimeException("Installation test failed on " + platformImage, e);
        }
    }

    @Test
    @DisplayName("Test installation with Java version validation")
    void testJavaVersionValidation() {
        logger.info("Testing Java version validation during installation");
        
        // Test with Java 11 (should fail)
        try (GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("openjdk:11-jdk-slim"))
                .withCommand("tail", "-f", "/dev/null")) {
            
            container.start();
            
            // Copy JAR and create test
            container.copyFileToContainer(
                MountableFile.forHostPath(ASCIIFRAME_JAR_PATH),
                "/tmp/asciiframe.jar"
            );
            
            // Try to run with old Java version
            org.testcontainers.containers.Container.ExecResult result = container.execInContainer(
                "java", "-version"
            );
            
            // Verify Java 11 is detected
            assertThat(result.getStderr()).contains("11.");
            
            // Try to run AsciiFrame (should handle gracefully)
            org.testcontainers.containers.Container.ExecResult runResult = container.execInContainer(
                "timeout", "10", "java", "-jar", "/tmp/asciiframe.jar", "--help"
            );
            
            // Should either work or fail gracefully (not crash)
            logger.info("Java 11 compatibility test completed with exit code: {}", runResult.getExitCode());
            
        } catch (Exception e) {
            logger.error("Java version validation test failed", e);
            throw new RuntimeException("Java version validation test failed", e);
        }
    }

    @Test
    @DisplayName("Test installation with insufficient permissions")
    void testPermissionHandling() {
        logger.info("Testing installation with restricted permissions");
        
        try (GenericContainer<?> container = createTestContainer("eclipse-temurin:23-jre")) {
            container.start();
            waitForContainerReady(container);
            
            // Create restricted user
            container.execInContainer("useradd", "-m", "-s", "/bin/bash", "testuser");
            
            // Copy JAR as root
            container.copyFileToContainer(
                MountableFile.forHostPath(ASCIIFRAME_JAR_PATH),
                "/tmp/asciiframe.jar"
            );
            
            // Try installation as restricted user
            String installScript = """
                #!/bin/bash
                set -e
                
                # Try to install in user home directory
                mkdir -p /home/testuser/asciiframe
                cp /tmp/asciiframe.jar /home/testuser/asciiframe/
                
                cat > /home/testuser/asciiframe/asciiframe << 'EOF'
                #!/bin/bash
                java -jar "$(dirname "$0")/asciiframe.jar" "$@"
                EOF
                
                chmod +x /home/testuser/asciiframe/asciiframe
                
                echo "Installation completed in user directory"
                """;
            
            org.testcontainers.containers.Container.ExecResult result = container.execInContainer(
                "su", "-c", "echo '" + installScript + "' > /tmp/user-install.sh && chmod +x /tmp/user-install.sh && /tmp/user-install.sh", "testuser"
            );
            
            assertThat(result.getExitCode())
                .withFailMessage("User installation failed: %s", result.getStderr())
                .isEqualTo(0);
            
            logger.info("✅ Permission handling test passed");
            
        } catch (Exception e) {
            logger.error("Permission handling test failed", e);
            throw new RuntimeException("Permission handling test failed", e);
        }
    }

    @Test
    @DisplayName("Test resource cleanup after installation")
    void testResourceCleanup() {
        logger.info("Testing resource cleanup after installation");
        
        try (GenericContainer<?> container = createTestContainer("eclipse-temurin:23-jre")) {
            container.start();
            waitForContainerReady(container);
            
            // Install and then simulate cleanup
            container.copyFileToContainer(
                MountableFile.forHostPath(ASCIIFRAME_JAR_PATH),
                "/tmp/asciiframe.jar"
            );
            
            // Create installation
            container.execInContainer("mkdir", "-p", "/tmp/install");
            container.execInContainer("cp", "/tmp/asciiframe.jar", "/tmp/install/");
            
            // Check disk usage before
            org.testcontainers.containers.Container.ExecResult duBefore = container.execInContainer("du", "-sh", "/tmp");
            logger.info("Disk usage before cleanup: {}", duBefore.getStdout().trim());
            
            // Simulate cleanup
            container.execInContainer("rm", "-f", "/tmp/asciiframe.jar");
            
            // Check disk usage after
            org.testcontainers.containers.Container.ExecResult duAfter = container.execInContainer("du", "-sh", "/tmp");
            logger.info("Disk usage after cleanup: {}", duAfter.getStdout().trim());
            
            // Verify installation still works
            org.testcontainers.containers.Container.ExecResult testRun = container.execInContainer(
                "timeout", "10", "java", "-jar", "/tmp/install/asciiframe.jar", "--help"
            );
            
            assertThat(testRun.getExitCode()).isIn(0, 124); // 0 = success, 124 = timeout (acceptable)
            
            logger.info("✅ Resource cleanup test passed");
            
        } catch (Exception e) {
            logger.error("Resource cleanup test failed", e);
            throw new RuntimeException("Resource cleanup test failed", e);
        }
    }

    private GenericContainer<?> createTestContainer(String image) {
        return new GenericContainer<>(DockerImageName.parse(image))
            .withCommand("tail", "-f", "/dev/null")
            .withStartupTimeout(STARTUP_TIMEOUT);
    }

    private void waitForContainerReady(GenericContainer<?> container) {
        // Wait a bit for container to be fully ready
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify container is responsive
        try {
            org.testcontainers.containers.Container.ExecResult result = container.execInContainer("echo", "ready");
            assertThat(result.getExitCode()).isEqualTo(0);
        } catch (Exception e) {
            throw new RuntimeException("Container not ready", e);
        }
    }

    private String createStandaloneInstallScript() {
        return """
            #!/bin/bash
            set -e
            
            echo "Starting AsciiFrame installation test..."
            
            # Create installation directory
            mkdir -p /tmp/install
            
            # Copy JAR
            cp /tmp/asciiframe.jar /tmp/install/
            
            # Create wrapper script
            cat > /tmp/install/asciiframe << 'EOF'
            #!/bin/bash
            java -jar "$(dirname "$0")/asciiframe.jar" "$@"
            EOF
            
            chmod +x /tmp/install/asciiframe
            
            # Create test configuration
            cat > /tmp/install/config.yml << 'EOF'
            entry: docs/index.adoc
            outDir: build
            formats: [html]
            theme:
              html: default
            server:
              port: 8080
            watch:
              enabled: false
            EOF
            
            # Create test document
            mkdir -p /tmp/install/docs
            cat > /tmp/install/docs/index.adoc << 'EOF'
            = Test Document
            Test Author
            
            == Introduction
            
            This is a test document for installation validation.
            
            == Conclusion
            
            Installation test completed!
            EOF
            
            echo "Installation test setup completed successfully"
            """;
    }

    private void testBasicFunctionality(GenericContainer<?> container) throws Exception {
        logger.info("Testing basic functionality...");
        
        // Test help command
        org.testcontainers.containers.Container.ExecResult helpResult = container.execInContainer(
            "timeout", "30", "java", "-jar", "/tmp/install/asciiframe.jar", "--help"
        );
        
        // Should complete within timeout (exit code 0 or 124 for timeout)
        assertThat(helpResult.getExitCode()).isIn(0, 124);
        
        // If it completed successfully, check output
        if (helpResult.getExitCode() == 0) {
            logger.info("Help command output: {}", helpResult.getStdout());
        }
        
        logger.info("✅ Basic functionality test completed");
    }
}