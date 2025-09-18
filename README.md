# AsciiFrame

A fast, zero-configuration AsciiDoc renderer with beautiful themes and live preview capabilities.

## Features

- 🚀 **Zero Configuration** - Works out of the box with sensible defaults
- 🎨 **Beautiful Themes** - Multiple professional themes for HTML and PDF output
- 📊 **Diagram Support** - Integrated Mermaid, PlantUML via Kroki
- ⚡ **Live Reload** - WebSocket-based live preview during development
- 🐳 **Docker Ready** - Available as container image
- 🔧 **HTTP API** - REST endpoints for integration
- 📱 **Multi-format** - Generate HTML and PDF from the same source

## Quick Start

### Option 1: One-liner Install (Recommended)

```bash
# Install anywhere - detects Java/Docker automatically
curl -sSL https://get.asciiframe.io | bash

# Then use
asciiframe           # Starts server
# → Visit http://localhost:8080/preview/index.html
```

### Option 2: GitHub Container Registry

```bash
# Direct run with GHCR image
docker run -p 8080:8080 \
  -v $PWD/docs:/work/docs \
  -v $PWD/build:/work/build \
  ghcr.io/remixxx31/asciiframework:latest
```

### Option 3: Docker Compose

```bash
# Add to docker-compose.yml
services:
  asciiframe:
    image: ghcr.io/remixxx31/asciiframework:latest
    ports: ["8080:8080"]
    volumes:
      - ./docs:/work/docs
      - ./output:/work/build

# Start
docker compose up -d
```

### Option 4: Direct JAR Download

```bash
# If you have Java 23+
curl -LO https://github.com/remixxx31/asciiframework/releases/latest/download/asciiframe.jar
java -jar asciiframe.jar
```

## Usage

1. **Create your content** in `docs/index.adoc`
2. **Start AsciiFrame** with any method above
3. **Visit** http://localhost:8080/preview/index.html
4. **Edit and save** - changes appear instantly

### API Usage

```bash
# Generate documentation
curl -X POST http://localhost:8080/render \
  -H "Content-Type: application/json" \
  -d '{"entry":"docs/index.adoc","formats":["html","pdf"]}'

# Health check
curl http://localhost:8080/health
```

## Configuration

Create `config.yml` to customize:

```yaml
entry: docs/index.adoc
outDir: build
formats: [html, pdf]

theme:
  html: documentation
  pdf: report

diagrams:
  engine: kroki
  url: http://localhost:8000

server:
  port: 8080
  compress: true

watch:
  enabled: true
  debounceMs: 500
```

## Requirements

- **Java 23+** (for JAR execution)
- **Docker** (for container usage)
- **Modern browser** (for live preview)

## Development

```bash
# Build from source
git clone https://github.com/remixxx31/asciiframework.git
cd asciiframework
./gradlew shadowJar

# Run
java -jar build/libs/app-fat.jar
```

## License

Voir rubrique

## Links

- **Documentation**: [GitHub Wiki](https://github.com/remixxx31/asciiframework/wiki)
- **Issues**: [GitHub Issues](https://github.com/remixxx31/asciiframework/issues)
- **Container Registry**: [GHCR](https://ghcr.io/remixxx31/asciiframework)
