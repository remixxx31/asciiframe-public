package co.remi.asciiframe.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Advanced test reporting framework for integration tests.
 * Generates comprehensive HTML and JSON reports.
 */
public class TestReportingFramework implements AfterAllCallback {
    
    private static final Logger logger = LoggerFactory.getLogger(TestReportingFramework.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Report configuration
    private static final String REPORTS_DIR = "build/reports/integration-tests";
    private static final String TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").format(Instant.now());
    
    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        logger.info("Generating integration test reports...");
        
        try {
            // Create reports directory
            Path reportsPath = Paths.get(REPORTS_DIR);
            Files.createDirectories(reportsPath);
            
            // Generate reports
            generateHtmlReport(reportsPath);
            generateJsonReport(reportsPath);
            generateMarkdownSummary(reportsPath);
            
            logger.info("✅ Integration test reports generated in: {}", reportsPath.toAbsolutePath());
            
        } catch (Exception e) {
            logger.error("Failed to generate test reports", e);
        }
    }
    
    private void generateHtmlReport(Path reportsPath) throws Exception {
        File htmlFile = reportsPath.resolve("integration-test-report-" + TIMESTAMP + ".html").toFile();
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(htmlFile))) {
            writer.println(createHtmlReport());
        }
        
        logger.info("HTML report generated: {}", htmlFile.getAbsolutePath());
    }
    
    private void generateJsonReport(Path reportsPath) throws Exception {
        File jsonFile = reportsPath.resolve("integration-test-results-" + TIMESTAMP + ".json").toFile();
        
        ObjectNode report = objectMapper.createObjectNode();
        report.put("timestamp", Instant.now().toString());
        report.put("testSuite", "AsciiFrame Integration Tests");
        report.put("version", getAsciiFrameVersion());
        
        // Add test metrics
        Map<String, IntegrationTestExtension.TestMetrics> allMetrics = IntegrationTestExtension.getAllMetrics();
        ObjectNode testClasses = report.putObject("testClasses");
        
        long totalTests = 0;
        long totalPassed = 0;
        long totalFailed = 0;
        Duration totalDuration = Duration.ZERO;
        
        for (Map.Entry<String, IntegrationTestExtension.TestMetrics> entry : allMetrics.entrySet()) {
            String className = entry.getKey();
            IntegrationTestExtension.TestMetrics metrics = entry.getValue();
            
            ObjectNode classNode = testClasses.putObject(className);
            classNode.put("totalTests", metrics.getTotalCount());
            classNode.put("passed", metrics.getPassedCount());
            classNode.put("failed", metrics.getFailedCount());
            classNode.put("duration", metrics.getTotalDuration().toMillis());
            
            // Add individual test results
            ObjectNode testsNode = classNode.putObject("tests");
            for (IntegrationTestExtension.TestResultRecord result : metrics.getTestResults()) {
                ObjectNode testNode = testsNode.putObject(result.getTestName());
                testNode.put("result", result.getResult().name());
                testNode.put("duration", result.getDuration().toMillis());
                if (result.getError() != null) {
                    testNode.put("error", result.getError().getMessage());
                }
            }
            
            totalTests += metrics.getTotalCount();
            totalPassed += metrics.getPassedCount();
            totalFailed += metrics.getFailedCount();
            totalDuration = totalDuration.plus(metrics.getTotalDuration());
        }
        
        // Add summary
        ObjectNode summary = report.putObject("summary");
        summary.put("totalTests", totalTests);
        summary.put("passed", totalPassed);
        summary.put("failed", totalFailed);
        summary.put("successRate", totalTests > 0 ? (double) totalPassed / totalTests * 100 : 0);
        summary.put("totalDuration", totalDuration.toMillis());
        
        // Add environment info
        ObjectNode environment = report.putObject("environment");
        environment.put("javaVersion", System.getProperty("java.version"));
        environment.put("osName", System.getProperty("os.name"));
        environment.put("osVersion", System.getProperty("os.version"));
        environment.put("osArch", System.getProperty("os.arch"));
        
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, report);
        logger.info("JSON report generated: {}", jsonFile.getAbsolutePath());
    }
    
    private void generateMarkdownSummary(Path reportsPath) throws Exception {
        File mdFile = reportsPath.resolve("INTEGRATION_TEST_SUMMARY.md").toFile();
        
        StringBuilder md = new StringBuilder();
        md.append("# AsciiFrame Integration Test Summary\n\n");
        md.append("**Generated:** ").append(Instant.now()).append("\n");
        md.append("**Version:** ").append(getAsciiFrameVersion()).append("\n\n");
        
        // Test results summary
        Map<String, IntegrationTestExtension.TestMetrics> allMetrics = IntegrationTestExtension.getAllMetrics();
        
        long totalTests = 0;
        long totalPassed = 0;
        long totalFailed = 0;
        Duration totalDuration = Duration.ZERO;
        
        for (IntegrationTestExtension.TestMetrics metrics : allMetrics.values()) {
            totalTests += metrics.getTotalCount();
            totalPassed += metrics.getPassedCount();
            totalFailed += metrics.getFailedCount();
            totalDuration = totalDuration.plus(metrics.getTotalDuration());
        }
        
        md.append("## Summary\n\n");
        md.append("| Metric | Value |\n");
        md.append("|--------|-------|\n");
        md.append("| Total Tests | ").append(totalTests).append(" |\n");
        md.append("| Passed | ").append(totalPassed).append(" |\n");
        md.append("| Failed | ").append(totalFailed).append(" |\n");
        md.append("| Success Rate | ").append(String.format("%.1f%%", totalTests > 0 ? (double) totalPassed / totalTests * 100 : 0)).append(" |\n");
        md.append("| Total Duration | ").append(formatDuration(totalDuration)).append(" |\n\n");
        
        // Test class details
        md.append("## Test Classes\n\n");
        for (Map.Entry<String, IntegrationTestExtension.TestMetrics> entry : allMetrics.entrySet()) {
            String className = entry.getKey();
            IntegrationTestExtension.TestMetrics metrics = entry.getValue();
            
            String status = metrics.getFailedCount() > 0 ? "❌" : "✅";
            md.append("### ").append(status).append(" ").append(className).append("\n\n");
            md.append("- **Tests:** ").append(metrics.getTotalCount()).append("\n");
            md.append("- **Passed:** ").append(metrics.getPassedCount()).append("\n");
            md.append("- **Failed:** ").append(metrics.getFailedCount()).append("\n");
            md.append("- **Duration:** ").append(formatDuration(metrics.getTotalDuration())).append("\n\n");
            
            // Failed tests details
            if (metrics.getFailedCount() > 0) {
                md.append("**Failed Tests:**\n\n");
                for (IntegrationTestExtension.TestResultRecord result : metrics.getTestResults()) {
                    if (result.getResult() == IntegrationTestExtension.TestResult.FAILED) {
                        md.append("- `").append(result.getTestName()).append("` - ");
                        if (result.getError() != null) {
                            md.append(result.getError().getMessage());
                        }
                        md.append("\n");
                    }
                }
                md.append("\n");
            }
        }
        
        // Environment info
        md.append("## Environment\n\n");
        md.append("- **Java Version:** ").append(System.getProperty("java.version")).append("\n");
        md.append("- **OS:** ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version")).append("\n");
        md.append("- **Architecture:** ").append(System.getProperty("os.arch")).append("\n");
        
        try (FileWriter writer = new FileWriter(mdFile)) {
            writer.write(md.toString());
        }
        
        logger.info("Markdown summary generated: {}", mdFile.getAbsolutePath());
    }
    
    private String createHtmlReport() {
        Map<String, IntegrationTestExtension.TestMetrics> allMetrics = IntegrationTestExtension.getAllMetrics();
        
        long totalTests = 0;
        long totalPassed = 0;
        long totalFailed = 0;
        Duration totalDuration = Duration.ZERO;
        
        for (IntegrationTestExtension.TestMetrics metrics : allMetrics.values()) {
            totalTests += metrics.getTotalCount();
            totalPassed += metrics.getPassedCount();
            totalFailed += metrics.getFailedCount();
            totalDuration = totalDuration.plus(metrics.getTotalDuration());
        }
        
        StringBuilder html = new StringBuilder();
        html.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>AsciiFrame Integration Test Report</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 20px; border-radius: 8px; }
                    .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; margin: 20px 0; }
                    .card { background: #f8f9fa; padding: 20px; border-radius: 8px; text-align: center; }
                    .card.success { background: #d4edda; border-left: 4px solid #28a745; }
                    .card.failure { background: #f8d7da; border-left: 4px solid #dc3545; }
                    .test-class { background: white; margin: 20px 0; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                    .test-result { display: flex; align-items: center; margin: 10px 0; }
                    .test-result.passed { color: #28a745; }
                    .test-result.failed { color: #dc3545; }
                    .icon { margin-right: 10px; font-size: 18px; }
                    .duration { color: #666; font-size: 0.9em; }
                    .error { background: #f8d7da; padding: 10px; border-radius: 4px; margin-top: 10px; font-family: monospace; font-size: 0.9em; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>🧪 AsciiFrame Integration Test Report</h1>
                    <p>Generated: """).append(Instant.now()).append("""
                    </p>
                    <p>Version: """).append(getAsciiFrameVersion()).append("""
                    </p>
                </div>
                
                <div class="summary">
                    <div class="card">
                        <h3>Total Tests</h3>
                        <h2>""").append(totalTests).append("""
                        </h2>
                    </div>
                    <div class="card success">
                        <h3>Passed</h3>
                        <h2>""").append(totalPassed).append("""
                        </h2>
                    </div>
                    <div class="card """).append(totalFailed > 0 ? "failure" : "success").append("""
                    ">
                        <h3>Failed</h3>
                        <h2>""").append(totalFailed).append("""
                        </h2>
                    </div>
                    <div class="card">
                        <h3>Success Rate</h3>
                        <h2>""").append(String.format("%.1f%%", totalTests > 0 ? (double) totalPassed / totalTests * 100 : 0)).append("""
                        </h2>
                    </div>
                    <div class="card">
                        <h3>Duration</h3>
                        <h2>""").append(formatDuration(totalDuration)).append("""
                        </h2>
                    </div>
                </div>
                
                <h2>Test Classes</h2>
            """);
        
        // Add test class details
        for (Map.Entry<String, IntegrationTestExtension.TestMetrics> entry : allMetrics.entrySet()) {
            String className = entry.getKey();
            IntegrationTestExtension.TestMetrics metrics = entry.getValue();
            
            html.append("<div class=\"test-class\">\n");
            html.append("<h3>").append(metrics.getFailedCount() > 0 ? "❌" : "✅").append(" ").append(className).append("</h3>\n");
            html.append("<p><strong>Duration:</strong> ").append(formatDuration(metrics.getTotalDuration())).append("</p>\n");
            
            for (IntegrationTestExtension.TestResultRecord result : metrics.getTestResults()) {
                String resultClass = result.getResult() == IntegrationTestExtension.TestResult.PASSED ? "passed" : "failed";
                String icon = result.getResult() == IntegrationTestExtension.TestResult.PASSED ? "✅" : "❌";
                
                html.append("<div class=\"test-result ").append(resultClass).append("\">\n");
                html.append("<span class=\"icon\">").append(icon).append("</span>\n");
                html.append("<span>").append(result.getTestName()).append("</span>\n");
                html.append("<span class=\"duration\"> (").append(formatDuration(result.getDuration())).append(")</span>\n");
                
                if (result.getError() != null) {
                    html.append("<div class=\"error\">").append(escapeHtml(result.getError().getMessage())).append("</div>\n");
                }
                
                html.append("</div>\n");
            }
            
            html.append("</div>\n");
        }
        
        html.append("""
                <div style="margin-top: 40px; text-align: center; color: #666;">
                    <p>Report generated by AsciiFrame Integration Test Framework</p>
                </div>
            </body>
            </html>
            """);
        
        return html.toString();
    }
    
    private String getAsciiFrameVersion() {
        // Try to get version from system property or default
        return System.getProperty("asciiframe.version", "development");
    }
    
    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long millis = duration.toMillis() % 1000;
        
        if (seconds > 0) {
            return String.format("%d.%03ds", seconds, millis);
        } else {
            return String.format("%dms", millis);
        }
    }
    
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }
    
    /**
     * Generate a quick summary report for CI/CD
     */
    public static String generateQuickSummary() {
        Map<String, IntegrationTestExtension.TestMetrics> allMetrics = IntegrationTestExtension.getAllMetrics();
        
        if (allMetrics.isEmpty()) {
            return "No test metrics available";
        }
        
        long totalTests = 0;
        long totalPassed = 0;
        long totalFailed = 0;
        
        for (IntegrationTestExtension.TestMetrics metrics : allMetrics.values()) {
            totalTests += metrics.getTotalCount();
            totalPassed += metrics.getPassedCount();
            totalFailed += metrics.getFailedCount();
        }
        
        String status = totalFailed == 0 ? "✅ PASSED" : "❌ FAILED";
        return String.format("%s - %d/%d tests passed (%.1f%%)", 
            status, totalPassed, totalTests, 
            totalTests > 0 ? (double) totalPassed / totalTests * 100 : 0);
    }
}