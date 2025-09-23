
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
        languageVersion.set(JavaLanguageVersion.of(21))
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
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("io.vertx:vertx-junit5:4.5.9")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("co.remi.asciiframe.App")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.shadowJar {
    archiveFileName.set("app-fat.jar")
    mergeServiceFiles()
}

tasks.test {
    useJUnitPlatform()
}
