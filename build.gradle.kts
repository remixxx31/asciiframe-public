
plugins {
    id("java")
    id("application")
    id("com.gradleup.shadow") version "8.3.0"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}

dependencies {
    implementation("io.vertx:vertx-core:4.5.9")
    implementation("io.vertx:vertx-web:4.5.9")
    implementation("io.vertx:vertx-web-client:4.5.9")
    implementation("io.vertx:vertx-config:4.5.9")

    implementation("org.asciidoctor:asciidoctorj:2.5.13")
    implementation("org.asciidoctor:asciidoctorj-pdf:2.3.9")

    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("org.yaml:snakeyaml:2.2")

    implementation("org.slf4j:slf4j-simple:2.0.12")
    
    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    
    // Integration test dependencies
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.0")
    testImplementation("org.awaitility:awaitility:4.2.0")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("ch.qos.logback:logback-classic:1.4.14")
}

application {
    mainClass.set("co.remi.asciiframe.App")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration", "installation", "docker")
    }
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

// Create integration test task
tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Runs integration tests"
    
    useJUnitPlatform {
        includeTags("integration")
    }
    
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
    
    // Run integration tests after unit tests
    shouldRunAfter(tasks.test)
    
    // Set system properties for integration tests
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
    systemProperty("testcontainers.reuse.enable", "true")
}

// Create installation test task
tasks.register<Test>("installationTest") {
    group = "verification"
    description = "Runs installation validation tests"
    
    useJUnitPlatform {
        includeTags("installation")
    }
    
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
    
    // Require fat JAR to be built first
    dependsOn(tasks.shadowJar)
    
    systemProperty("asciiframe.jar.path", "${layout.buildDirectory.get()}/libs/app-fat.jar")
    systemProperty("testcontainers.reuse.enable", "true")
}

tasks.shadowJar {
    archiveFileName.set("app-fat.jar")
    mergeServiceFiles()
}
