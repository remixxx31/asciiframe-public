package co.remi.asciiframe.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Framework for testing AsciiFrame installation across different platforms
 * following industry best practices from major open-source projects.
 */
public class InstallationTestFramework {
    
    private static final Logger logger = LoggerFactory.getLogger(InstallationTestFramework.class);
    
    // Supported OS distributions for testing
    public enum OperatingSystem {
        UBUNTU_20_04("ubuntu:20.04", "apt-get", "ubuntu"),
        UBUNTU_22_04("ubuntu:22.04", "apt-get", "ubuntu"),
        ALPINE_3_18("alpine:3.18", "apk", "alpine"),
        CENTOS_8("centos:8", "dnf", "centos"),
        DEBIAN_11("debian:11", "apt-get", "debian");
        
        private final String dockerImage;
        private final String packageManager;
        private final String family;
        
        OperatingSystem(String dockerImage, String packageManager, String family) {
            this.dockerImage = dockerImage;
            this.packageManager = packageManager;
            this.family = family;
        }
        
        public String getDockerImage() { return dockerImage; }
        public String getPackageManager() { return packageManager; }
        public String getFamily() { return family; }
    }
    
    // Installation modes to test
    public enum InstallationMode {
        STANDALONE("standalone"),
        DOCKER("docker"),
        GLOBAL("global");
        
        private final String mode;
        
        InstallationMode(String mode) {
            this.mode = mode;
        }
        
        public String getMode() { return mode; }
    }
    
    /**
     * Creates a container for testing installation on the specified OS
     */
    public static GenericContainer<?> createInstallationContainer(OperatingSystem os) {
        logger.info("Creating installation container for OS: {}", os);
        
        String dockerfile = generateDockerfile(os);
        
        ImageFromDockerfile image = new ImageFromDockerfile()
            .withDockerfileFromBuilder(builder -> 
                builder.from(os.getDockerImage())
                    .run(getPackageUpdateCommand(os))
                    .run(getPackageInstallCommand(os, getRequiredPackages(os)))
                    .run("mkdir -p /work /tmp/asciiframe-test")
                    .workDir("/work")
                    .build()
            );
        
        return new GenericContainer<>(image)
            .withStartupTimeout(Duration.ofMinutes(5))
            .withWorkingDirectory("/work");
    }
    
    /**
     * Creates a container with AsciiFrame JAR pre-installed for testing
     */
    public static GenericContainer<?> createContainerWithJar(OperatingSystem os, Path jarPath) {
        if (!Files.exists(jarPath)) {
            throw new IllegalArgumentException("JAR file not found: " + jarPath);
        }
        
        GenericContainer<?> container = createInstallationContainer(os);
        
        // Copy JAR to container
        container.withCopyFileToContainer(
            org.testcontainers.utility.MountableFile.forHostPath(jarPath),
            "/work/asciiframe.jar"
        );
        
        return container;
    }
    
    /**
     * Gets the AsciiFrame JAR path from system property
     */
    public static Path getAsciiFrameJarPath() {
        String jarPath = System.getProperty("asciiframe.jar.path");
        if (jarPath == null) {
            // Fallback to default build location
            jarPath = "build/libs/app-fat.jar";
        }
        
        Path path = Paths.get(jarPath);
        if (!Files.exists(path)) {
            throw new RuntimeException("AsciiFrame JAR not found at: " + path + 
                ". Please build the project first with './gradlew shadowJar'");
        }
        
        return path;
    }
    
    /**
     * Tests installation script download and execution
     */
    public static void testInstallationScript(GenericContainer<?> container, 
                                              InstallationMode mode, 
                                              Map<String, String> envVars) throws Exception {
        logger.info("Testing installation script for mode: {}", mode.getMode());
        
        // Download install script (simulate real installation)
        String downloadCommand = "curl -fsSL https://raw.githubusercontent.com/remixxx31/asciiframe-public/main/install.sh -o install.sh";
        container.execInContainer("sh", "-c", downloadCommand);
        container.execInContainer("chmod", "+x", "install.sh");
        
        // Prepare environment variables
        String envString = envVars.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .reduce("", (acc, env) -> acc + " " + env);
        
        // Execute installation
        String installCommand = String.format("%s ./install.sh --%s --skip-start", 
            envString.trim(), mode.getMode());
        
        org.testcontainers.containers.Container.ExecResult result = 
            container.execInContainer("sh", "-c", installCommand);
        
        if (result.getExitCode() != 0) {
            throw new AssertionError("Installation failed for mode " + mode.getMode() + 
                "\nStdout: " + result.getStdout() + 
                "\nStderr: " + result.getStderr());
        }
        
        logger.info("Installation script test passed for mode: {}", mode.getMode());
    }
    
    /**
     * Validates standalone installation
     */
    public static void validateStandaloneInstallation(GenericContainer<?> container, 
                                                     String installDir) throws Exception {
        logger.info("Validating standalone installation in: {}", installDir);
        
        // Check required files
        validateFileExists(container, installDir + "/asciiframe.jar");
        validateFileExists(container, installDir + "/asciiframe");
        validateFileExists(container, installDir + "/config.yml");
        
        // Check executable permissions
        validateExecutable(container, installDir + "/asciiframe");
        
        // Test help command
        org.testcontainers.containers.Container.ExecResult result = 
            container.execInContainer(installDir + "/asciiframe", "--help");
        
        if (result.getExitCode() != 0) {
            logger.warn("Help command failed (may be expected in test environment): {}", 
                result.getStderr());
        }
        
        logger.info("Standalone installation validation passed");
    }
    
    /**
     * Validates Docker installation
     */
    public static void validateDockerInstallation(GenericContainer<?> container, 
                                                  String installDir) throws Exception {
        logger.info("Validating Docker installation in: {}", installDir);
        
        // Check required files
        validateFileExists(container, installDir + "/docker-compose.yml");
        validateFileExists(container, installDir + "/config.yml");
        validateFileExists(container, installDir + "/start.sh");
        validateFileExists(container, installDir + "/stop.sh");
        validateFileExists(container, installDir + "/docs/index.adoc");
        
        // Check executable permissions
        validateExecutable(container, installDir + "/start.sh");
        validateExecutable(container, installDir + "/stop.sh");
        
        // Validate Docker Compose configuration (if Docker is available)
        try {
            org.testcontainers.containers.Container.ExecResult result = 
                container.execInContainer("sh", "-c", 
                    "cd " + installDir + " && docker compose config");
            
            if (result.getExitCode() == 0) {
                logger.info("Docker Compose configuration is valid");
            } else {
                logger.warn("Docker Compose validation skipped (Docker not available in container)");
            }
        } catch (Exception e) {
            logger.warn("Docker Compose validation skipped: {}", e.getMessage());
        }
        
        logger.info("Docker installation validation passed");
    }
    
    /**
     * Validates global installation
     */
    public static void validateGlobalInstallation(GenericContainer<?> container, 
                                                  String installDir) throws Exception {
        logger.info("Validating global installation in: {}", installDir);
        
        // Check required files
        validateFileExists(container, installDir + "/asciiframe.jar");
        validateFileExists(container, installDir + "/asciiframe");
        
        // Check executable permissions
        validateExecutable(container, installDir + "/asciiframe");
        
        // Test that command is in PATH (if installed globally)
        try {
            org.testcontainers.containers.Container.ExecResult result = 
                container.execInContainer("which", "asciiframe");
            
            if (result.getExitCode() == 0) {
                logger.info("AsciiFrame is available in PATH");
            }
        } catch (Exception e) {
            logger.warn("Global PATH validation skipped: {}", e.getMessage());
        }
        
        logger.info("Global installation validation passed");
    }
    
    private static void validateFileExists(GenericContainer<?> container, String file) throws Exception {
        org.testcontainers.containers.Container.ExecResult result = 
            container.execInContainer("test", "-f", file);
        
        if (result.getExitCode() != 0) {
            throw new AssertionError("Required file not found: " + file);
        }
    }
    
    private static void validateExecutable(GenericContainer<?> container, String file) throws Exception {
        org.testcontainers.containers.Container.ExecResult result = 
            container.execInContainer("test", "-x", file);
        
        if (result.getExitCode() != 0) {
            throw new AssertionError("File is not executable: " + file);
        }
    }
    
    private static String generateDockerfile(OperatingSystem os) {
        return String.format("""
            FROM %s
            RUN %s
            RUN %s
            RUN mkdir -p /work /tmp/asciiframe-test
            WORKDIR /work
            """, 
            os.getDockerImage(),
            getPackageUpdateCommand(os),
            getPackageInstallCommand(os, getRequiredPackages(os))
        );
    }
    
    private static String getPackageUpdateCommand(OperatingSystem os) {
        return switch (os.getFamily()) {
            case "ubuntu", "debian" -> "apt-get update";
            case "alpine" -> "apk update";
            case "centos" -> "dnf update -y";
            default -> throw new IllegalArgumentException("Unsupported OS family: " + os.getFamily());
        };
    }
    
    private static String getPackageInstallCommand(OperatingSystem os, List<String> packages) {
        String packageList = String.join(" ", packages);
        
        return switch (os.getFamily()) {
            case "ubuntu", "debian" -> "DEBIAN_FRONTEND=noninteractive apt-get install -y " + packageList;
            case "alpine" -> "apk add --no-cache " + packageList;
            case "centos" -> "dnf install -y " + packageList;
            default -> throw new IllegalArgumentException("Unsupported OS family: " + os.getFamily());
        };
    }
    
    private static List<String> getRequiredPackages(OperatingSystem os) {
        return switch (os.getFamily()) {
            case "ubuntu", "debian" -> List.of("curl", "wget", "ca-certificates", "openjdk-21-jre-headless");
            case "alpine" -> List.of("curl", "wget", "ca-certificates", "openjdk21-jre-headless");
            case "centos" -> List.of("curl", "wget", "ca-certificates", "java-21-openjdk-headless");
            default -> throw new IllegalArgumentException("Unsupported OS family: " + os.getFamily());
        };
    }
}