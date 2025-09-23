package co.remi.asciiframe.integration;

import org.junit.jupiter.api.extension.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * JUnit 5 extension for integration tests.
 * Provides test lifecycle management, timing, and reporting capabilities.
 */
public class IntegrationTestExtension implements BeforeAllCallback, AfterAllCallback, 
    BeforeEachCallback, AfterEachCallback, TestWatcher {
    
    private static final Logger logger = LoggerFactory.getLogger(IntegrationTestExtension.class);
    
    // Thread-safe storage for test metrics
    private static final ConcurrentMap<String, TestMetrics> testMetrics = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Instant> testStartTimes = new ConcurrentHashMap<>();
    
    @Override
    public void beforeAll(ExtensionContext context) {
        String className = context.getDisplayName();
        logger.info("Starting integration test class: {}", className);
        
        // Record class start time
        testMetrics.put(className, new TestMetrics(className, Instant.now()));
    }
    
    @Override
    public void afterAll(ExtensionContext context) {
        String className = context.getDisplayName();
        TestMetrics metrics = testMetrics.get(className);
        
        if (metrics != null) {
            metrics.setEndTime(Instant.now());
            Duration duration = Duration.between(metrics.getStartTime(), metrics.getEndTime());
            
            logger.info("Completed integration test class: {} in {} ms", 
                className, duration.toMillis());
        }
        
        // Cleanup any resources if needed
        cleanupTestResources(context);
    }
    
    @Override
    public void beforeEach(ExtensionContext context) {
        String testKey = getTestKey(context);
        testStartTimes.put(testKey, Instant.now());
        
        logger.debug("Starting test: {}", context.getDisplayName());
    }
    
    @Override
    public void afterEach(ExtensionContext context) {
        String testKey = getTestKey(context);
        Instant startTime = testStartTimes.remove(testKey);
        
        if (startTime != null) {
            Duration duration = Duration.between(startTime, Instant.now());
            logger.debug("Completed test: {} in {} ms", 
                context.getDisplayName(), duration.toMillis());
        }
    }
    
    @Override
    public void testSuccessful(ExtensionContext context) {
        String testKey = getTestKey(context);
        logger.info("✅ Test PASSED: {}", context.getDisplayName());
        
        // Record success metrics
        recordTestResult(context, TestResult.PASSED);
    }
    
    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        String testKey = getTestKey(context);
        logger.error("❌ Test FAILED: {} - {}", context.getDisplayName(), cause.getMessage());
        
        // Record failure metrics with error details
        recordTestResult(context, TestResult.FAILED, cause);
        
        // Collect failure artifacts
        collectFailureArtifacts(context, cause);
    }
    
    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        String testKey = getTestKey(context);
        logger.warn("⚠️ Test ABORTED: {} - {}", context.getDisplayName(), cause.getMessage());
        
        recordTestResult(context, TestResult.ABORTED, cause);
    }
    
    @Override
    public void testDisabled(ExtensionContext context, java.util.Optional<String> reason) {
        logger.info("⏭️ Test DISABLED: {} - {}", context.getDisplayName(), 
            reason.orElse("No reason provided"));
            
        recordTestResult(context, TestResult.DISABLED);
    }
    
    private String getTestKey(ExtensionContext context) {
        return context.getRequiredTestClass().getSimpleName() + "#" + context.getDisplayName();
    }
    
    private void recordTestResult(ExtensionContext context, TestResult result) {
        recordTestResult(context, result, null);
    }
    
    private void recordTestResult(ExtensionContext context, TestResult result, Throwable error) {
        String className = context.getRequiredTestClass().getSimpleName();
        TestMetrics metrics = testMetrics.get(className);
        
        if (metrics != null) {
            String testName = context.getDisplayName();
            String testKey = getTestKey(context);
            Instant startTime = testStartTimes.get(testKey);
            
            if (startTime != null) {
                Duration duration = Duration.between(startTime, Instant.now());
                metrics.addTestResult(testName, result, duration, error);
            }
        }
    }
    
    private void collectFailureArtifacts(ExtensionContext context, Throwable cause) {
        try {
            // Create failure report
            String testKey = getTestKey(context);
            String timestamp = Instant.now().toString();
            
            StringBuilder artifactInfo = new StringBuilder();
            artifactInfo.append("Test Failure Artifact\n");
            artifactInfo.append("=====================\n");
            artifactInfo.append("Test: ").append(context.getDisplayName()).append("\n");
            artifactInfo.append("Class: ").append(context.getRequiredTestClass().getName()).append("\n");
            artifactInfo.append("Time: ").append(timestamp).append("\n");
            artifactInfo.append("Error: ").append(cause.getMessage()).append("\n");
            artifactInfo.append("\nStack Trace:\n");
            
            for (StackTraceElement element : cause.getStackTrace()) {
                artifactInfo.append("  ").append(element.toString()).append("\n");
            }
            
            // Store artifact information (could be written to file system if needed)
            logger.debug("Failure artifact collected for test: {}", testKey);
            
        } catch (Exception e) {
            logger.warn("Failed to collect failure artifacts for test: {}", context.getDisplayName(), e);
        }
    }
    
    private void cleanupTestResources(ExtensionContext context) {
        // Perform any necessary cleanup
        // This could include stopping containers, cleaning temp files, etc.
        logger.debug("Cleaning up resources for test class: {}", context.getDisplayName());
    }
    
    /**
     * Get test metrics for reporting
     */
    public static TestMetrics getMetrics(String className) {
        return testMetrics.get(className);
    }
    
    /**
     * Get all test metrics
     */
    public static java.util.Map<String, TestMetrics> getAllMetrics() {
        return new java.util.HashMap<>(testMetrics);
    }
    
    /**
     * Test result enumeration
     */
    public enum TestResult {
        PASSED, FAILED, ABORTED, DISABLED
    }
    
    /**
     * Test metrics collection class
     */
    public static class TestMetrics {
        private final String className;
        private final Instant startTime;
        private Instant endTime;
        private final java.util.List<TestResultRecord> testResults = new java.util.ArrayList<>();
        
        public TestMetrics(String className, Instant startTime) {
            this.className = className;
            this.startTime = startTime;
        }
        
        public void setEndTime(Instant endTime) {
            this.endTime = endTime;
        }
        
        public void addTestResult(String testName, TestResult result, Duration duration, Throwable error) {
            testResults.add(new TestResultRecord(testName, result, duration, error));
        }
        
        public String getClassName() { return className; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public java.util.List<TestResultRecord> getTestResults() { return new java.util.ArrayList<>(testResults); }
        
        public long getPassedCount() {
            return testResults.stream().mapToLong(r -> r.result == TestResult.PASSED ? 1 : 0).sum();
        }
        
        public long getFailedCount() {
            return testResults.stream().mapToLong(r -> r.result == TestResult.FAILED ? 1 : 0).sum();
        }
        
        public long getTotalCount() {
            return testResults.size();
        }
        
        public Duration getTotalDuration() {
            if (endTime != null) {
                return Duration.between(startTime, endTime);
            }
            return Duration.ZERO;
        }
    }
    
    /**
     * Individual test result record
     */
    public static class TestResultRecord {
        private final String testName;
        private final TestResult result;
        private final Duration duration;
        private final Throwable error;
        
        public TestResultRecord(String testName, TestResult result, Duration duration, Throwable error) {
            this.testName = testName;
            this.result = result;
            this.duration = duration;
            this.error = error;
        }
        
        public String getTestName() { return testName; }
        public TestResult getResult() { return result; }
        public Duration getDuration() { return duration; }
        public Throwable getError() { return error; }
    }
}