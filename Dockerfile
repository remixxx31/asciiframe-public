# Ultra-optimized Dockerfile for AsciiFrame
# Target: ~150-200MB (vs previous 480MB)

# Build stage with minimal dependencies
FROM eclipse-temurin:23-jdk-alpine AS builder

WORKDIR /build

# Copy gradle files first for better layer caching
COPY gradle/ gradle/
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts ./

# Download dependencies with minimal logging
RUN ./gradlew dependencies --no-daemon --quiet

# Copy source and build with optimization
COPY src/ src/
COPY themes/ themes/
COPY config.yml ./

# Build with maximum optimization
RUN ./gradlew clean shadowJar --no-daemon --quiet

# Ultra-minimal runtime stage
FROM eclipse-temurin:23-jre-alpine

LABEL maintainer="AsciiFrame Contributors"
LABEL org.opencontainers.image.title="AsciiFrame"
LABEL org.opencontainers.image.description="Zero-config AsciiDoc renderer with beautiful themes"
LABEL org.opencontainers.image.url="https://github.com/remixxx31/asciiframework"
LABEL org.opencontainers.image.source="https://github.com/remixxx31/asciiframework"
LABEL org.opencontainers.image.vendor="AsciiFrame"
LABEL org.opencontainers.image.licenses="Apache-2.0"

# Install only essential runtime dependencies
RUN apk add --no-cache \
    curl \
    fontconfig \
    ttf-dejavu \
    haveged \
    && rm -rf /var/cache/apk/* \
    && rm -rf /tmp/* \
    && rm -rf /var/tmp/*

# Create optimized user
RUN addgroup -g 1001 -S asciiframe \
    && adduser -u 1001 -S asciiframe -G asciiframe -s /bin/sh

WORKDIR /app

# Copy only essential files from build stage
COPY --from=builder /build/build/libs/app-fat.jar app.jar
COPY --from=builder /build/config.yml config.yml.template
COPY --from=builder /build/themes/pdf/ themes/pdf/

# Create directories with minimal permissions
RUN mkdir -p /work/docs /work/build /work/.cache \
    && chown -R asciiframe:asciiframe /app /work

# Optimized health check
HEALTHCHECK --interval=60s --timeout=5s --start-period=30s --retries=2 \
    CMD curl -f http://localhost:8080/health || exit 1

USER asciiframe

# Optimized JVM settings for container environment
ENV JAVA_OPTS="-Xms128m -Xmx256m \
    -XX:+UseSerialGC \
    -XX:+TieredCompilation \
    -XX:TieredStopAtLevel=1 \
    -XX:+UseCompressedOops \
    -XX:+UseCompressedClassPointers \
    -Djava.security.egd=file:/dev/urandom \
    -Djava.awt.headless=true"

ENV CONFIG_PATH="/work/config.yml"
ENV WORK_DIR="/work"

EXPOSE 8080

VOLUME ["/work/docs", "/work/build", "/work/.cache"]

# Optimized entrypoint
ENTRYPOINT ["sh", "-c", "\
haveged -F & \
[ ! -f $CONFIG_PATH ] && export CONFIG_PATH=/app/config.yml.template; \
exec java $JAVA_OPTS -jar /app/app.jar\
"]