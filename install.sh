#!/bin/bash
# AsciiFrame Professional Install Script
# Usage: curl -sSL https://get.asciiframe.io | bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
ASCIIFRAME_VERSION=${ASCIIFRAME_VERSION:-"latest"}
INSTALL_DIR=${INSTALL_DIR:-"./asciiframe"}
DOCKER_COMPOSE=${DOCKER_COMPOSE:-"false"}
GLOBAL_INSTALL=${GLOBAL_INSTALL:-"false"}
REPO_URL="https://github.com/remixxx31/asciiframe-public"
RELEASES_URL="$REPO_URL/releases"

print_header() {
    echo -e "${BLUE}"
    echo "🎯 AsciiFrame Quick Installer"
    echo "========================================="
    echo -e "${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

detect_os() {
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        OS="linux"
        ARCH=$(uname -m)
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        OS="macos"
        ARCH=$(uname -m)
    elif [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "cygwin" ]]; then
        OS="windows"
        ARCH="amd64"
    else
        print_error "Unsupported OS: $OSTYPE"
        exit 1
    fi
    
    # Normalize architecture
    case $ARCH in
        x86_64|amd64) ARCH="amd64" ;;
        arm64|aarch64) ARCH="arm64" ;;
        *) print_error "Unsupported architecture: $ARCH"; exit 1 ;;
    esac
}

check_dependencies() {
    print_header
    echo "Detecting system..."
    
    detect_os
    print_success "Detected: $OS-$ARCH"
    
    # Check for curl
    if ! command -v curl &> /dev/null; then
        print_error "curl is required but not installed"
        exit 1
    fi
    
    # Check for Docker if Docker mode requested
    if [[ "$DOCKER_COMPOSE" == "true" ]]; then
        if ! command -v docker &> /dev/null; then
            print_error "Docker is required but not installed"
            exit 1
        fi
        print_success "Docker found"
    else
        # Check for Java if standalone mode
        if ! command -v java &> /dev/null; then
            print_warning "Java not found. Will install Docker mode instead."
            DOCKER_COMPOSE="true"
        else
            JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
            if [[ $JAVA_VERSION -lt 21 ]]; then
                print_warning "Java $JAVA_VERSION found, but Java 21+ required. Will install Docker mode instead."
                DOCKER_COMPOSE="true"
            else
                print_success "Java $JAVA_VERSION found (compatible)"
            fi
        fi
    fi
}

create_install_directory() {
    if [[ "$DOCKER_COMPOSE" == "true" ]]; then
        # For Docker mode, create project directory
        echo "Creating project directory: $INSTALL_DIR"
        mkdir -p "$INSTALL_DIR"
        cd "$INSTALL_DIR"
    else
        # For standalone mode, just ensure install directory exists
        echo "Installing to: $INSTALL_DIR"
        mkdir -p "$INSTALL_DIR"
    fi
}

install_docker_mode() {
    echo "Installing AsciiFrame in Docker mode..."
    
    # Create docker-compose.yml
    cat > docker-compose.yml << 'EOF'
version: '3.8'

services:
  asciiframe:
    image: ghcr.io/remixxx31/asciiframework:latest
    ports:
      - "8080:8080"
    volumes:
      - ./docs:/work/docs
      - ./output:/work/build
      - ./config.yml:/work/config.yml:ro
    environment:
      - CONFIG_PATH=/work/config.yml
      - JAVA_OPTS=-Xms256m -Xmx512m
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "sh", "-c", "wget --quiet --tries=1 --spider http://localhost:8080/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 3

  # Kroki for diagrams (optional)
  kroki:
    image: yuzutech/kroki
    ports:
      - "8000:8000"
    restart: unless-stopped
EOF

    # Create default config
    cat > config.yml << 'EOF'
# AsciiFrame Configuration
# Documentation: https://github.com/remixxx31/asciiframe-public

# Entry point - your main AsciiDoc file
entry: docs/index.adoc

# Output directory for generated files
outDir: build

# Output formats to generate
# Options: [html], [pdf], [html, pdf]
formats: [html, pdf]

# Theme configuration
theme:
  # HTML themes: documentation, modern, minimal, dark, presentation
  html: documentation
  # PDF themes: report, book, article, minimal
  pdf: report

# Diagram integration with Kroki
diagrams:
  engine: kroki
  url: http://kroki:8000
  # Cache diagrams locally (uncomment to enable)
  # cache: true

# File watching for live reload
watch:
  # Enable/disable live reload
  enabled: true
  # Delay before rebuilding (milliseconds)
  debounceMs: 500

# Web server configuration  
server:
  # Port to serve on
  port: 8080
  # Enable gzip compression
  compress: true
  # CORS settings (uncomment if needed)
  # cors:
  #   enabled: true
  #   origins: ["http://localhost:3000"]

# ========================================
# QUICK THEME SWITCHING
# ========================================
# Uncomment to try different themes:

# Modern theme (clean, responsive)
# theme:
#   html: modern
#   pdf: book

# Dark theme (dark background)  
# theme:
#   html: dark
#   pdf: article

# Minimal theme (simple, lightweight)
# theme:
#   html: minimal
#   pdf: minimal

# ========================================
# ADDING NEW PAGES
# ========================================
# 1. Create new .adoc files in docs/ directory
# 2. Link them in your main index.adoc:
#    
#    include::chapter1.adoc[]
#    include::chapter2.adoc[]
#
# 3. Or create a multi-page structure:
#    docs/
#    ├── index.adoc          (main page)
#    ├── getting-started.adoc
#    └── guides/
#        └── tutorial.adoc
#
# 4. Each page accessible via:
#    http://localhost:8080/preview/getting-started.html
#    http://localhost:8080/preview/guides/tutorial.html
#
# 5. Cross-reference between pages:
#    <<getting-started#setup,Setup Guide>>
#    xref:guides/tutorial.adoc[Tutorial]
EOF

    # Create docs directory with sample
    mkdir -p docs
    cat > docs/index.adoc << 'EOF'
= Welcome to AsciiFrame
Your Name
:toc:
:icons: font

== Getting Started

Congratulations! AsciiFrame is now installed and ready to use.

== Quick Test

Edit this file and save to see live reload in action.

== Next Steps

1. Visit http://localhost:8080/preview/index.html
2. Edit `docs/index.adoc`
3. Watch the magic happen!

[source,bash]
----
# Generate documentation
curl -X POST http://localhost:8080/render \
  -H "Content-Type: application/json" \
  -d '{"entry":"docs/index.adoc","formats":["html","pdf"]}'
----
EOF

    # Create convenience scripts
    cat > start.sh << 'EOF'
#!/bin/bash
echo "🚀 Starting AsciiFrame..."
docker compose up -d

echo "⏳ Waiting for service..."
sleep 5

if curl -s http://localhost:8080/health > /dev/null; then
    echo "✅ AsciiFrame is running!"
    echo "   📄 View docs: http://localhost:8080/preview/index.html"
    echo "   🔧 API: http://localhost:8080/render"
    echo "   📊 Health: http://localhost:8080/health"
else
    echo "❌ Service failed to start. Check logs with: docker compose logs"
fi
EOF

    cat > stop.sh << 'EOF'
#!/bin/bash
echo "🛑 Stopping AsciiFrame..."
docker compose down
echo "✅ Stopped"
EOF

    chmod +x start.sh stop.sh
    
    print_success "Docker mode installation complete"
}

get_latest_version() {
    if [[ "$ASCIIFRAME_VERSION" == "latest" ]]; then
        # Get latest release tag from GitHub API
        ASCIIFRAME_VERSION=$(curl -s "https://api.github.com/repos/remixxx31/asciiframe-public/releases/latest" | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/')
        if [[ -z "$ASCIIFRAME_VERSION" ]]; then
            print_error "Failed to get latest version"
            exit 1
        fi
        print_success "Latest version: $ASCIIFRAME_VERSION"
    fi
}

install_standalone_mode() {
    echo "Installing AsciiFrame in standalone mode..."
    
    get_latest_version
    
    # Adjust install directory for global install
    if [[ "$GLOBAL_INSTALL" == "true" ]]; then
        if [[ $EUID -eq 0 ]]; then
            INSTALL_DIR="/usr/local/bin"
        else
            INSTALL_DIR="$HOME/.local/bin"
            # Add to PATH if needed
            if [[ ":$PATH:" != *":$INSTALL_DIR:"* ]]; then
                echo "export PATH=\"$INSTALL_DIR:\$PATH\"" >> "$HOME/.bashrc"
                echo "export PATH=\"$INSTALL_DIR:\$PATH\"" >> "$HOME/.zshrc" 2>/dev/null || true
                export PATH="$INSTALL_DIR:$PATH"
                print_warning "Added $INSTALL_DIR to PATH. Restart your shell or run: export PATH=\"$INSTALL_DIR:\$PATH\""
            fi
        fi
    fi
    
    mkdir -p "$INSTALL_DIR"
    
    # Download JAR
    echo "Downloading AsciiFrame $ASCIIFRAME_VERSION..."
    JAR_URL="$RELEASES_URL/download/$ASCIIFRAME_VERSION/asciiframe.jar"
    
    if ! curl -fsSL "$JAR_URL" -o "$INSTALL_DIR/asciiframe.jar"; then
        print_error "Failed to download AsciiFrame JAR from $JAR_URL"
        exit 1
    fi
    
    # Create wrapper script
    cat > "$INSTALL_DIR/asciiframe" << 'EOF'
#!/bin/bash
# AsciiFrame Wrapper Script

# Find the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="$SCRIPT_DIR/asciiframe.jar"

# Check if JAR exists
if [[ ! -f "$JAR_PATH" ]]; then
    echo "Error: AsciiFrame JAR not found at $JAR_PATH"
    exit 1
fi

# Check Java version
if ! command -v java &> /dev/null; then
    echo "Error: Java is required but not installed"
    exit 1
fi

# Execute AsciiFrame
exec java -jar "$JAR_PATH" "$@"
EOF
    
    chmod +x "$INSTALL_DIR/asciiframe"
    
    # Create config
    cat > config.yml << 'EOF'
# AsciiFrame Configuration
# Documentation: https://github.com/remixxx31/asciiframe-public

# Entry point - your main AsciiDoc file
entry: docs/index.adoc

# Output directory for generated files
outDir: output

# Output formats to generate
# Options: [html], [pdf], [html, pdf]
formats: [html, pdf]

# Theme configuration
theme:
  # HTML themes: documentation, modern, minimal, dark, presentation
  html: documentation
  # PDF themes: report, book, article, minimal
  pdf: report

# File watching for live reload
watch:
  # Enable/disable live reload
  enabled: true
  # Delay before rebuilding (milliseconds)
  debounceMs: 500

# Web server configuration  
server:
  # Port to serve on
  port: 8080
  # Enable gzip compression
  # compress: true
  # CORS settings (uncomment if needed)
  # cors:
  #   enabled: true
  #   origins: ["http://localhost:3000"]

# Diagram integration (optional)
# Requires Kroki service running
# diagrams:
#   engine: kroki
#   url: http://localhost:8000
#   cache: true

# ========================================
# QUICK THEME SWITCHING
# ========================================
# Uncomment to try different themes:

# Modern theme (clean, responsive)
# theme:
#   html: modern
#   pdf: book

# Dark theme (dark background)  
# theme:
#   html: dark
#   pdf: article

# Minimal theme (simple, lightweight)
# theme:
#   html: minimal
#   pdf: minimal

# ========================================
# ADDING NEW PAGES
# ========================================
# 1. Create new .adoc files in docs/ directory
# 2. Link them in your main index.adoc:
#    
#    include::chapter1.adoc[]
#    include::chapter2.adoc[]
#
# 3. Or create a multi-page structure:
#    docs/
#    ├── index.adoc          (main page)
#    ├── getting-started.adoc
#    └── guides/
#        └── tutorial.adoc
#
# 4. Each page accessible via:
#    http://localhost:8080/preview/getting-started.html
#    http://localhost:8080/preview/guides/tutorial.html
#
# 5. Cross-reference between pages:
#    <<getting-started#setup,Setup Guide>>
#    xref:guides/tutorial.adoc[Tutorial]
EOF

    # Create docs directory
    mkdir -p docs output
    cat > docs/index.adoc << 'EOF'
= Welcome to AsciiFrame
Your Name
:toc:
:icons: font

== Getting Started

Congratulations! AsciiFrame is now installed and ready to use.

== Quick Test

Edit this file and save to see live reload in action.

== Next Steps

1. Visit http://localhost:8080/preview/index.html
2. Edit `docs/index.adoc`
3. Watch the magic happen!
EOF

    # Create project template if in current directory
    if [[ "$INSTALL_DIR" == "$HOME/.local/bin" ]] && [[ ! -f "docs/index.adoc" ]]; then
        create_project_template
    fi
    
    print_success "Standalone mode installation complete"
}

generate_initial_docs() {
    echo "Generating initial documentation..."
    
    if [[ "$DOCKER_COMPOSE" == "true" ]]; then
        ./start.sh
        sleep 5
        curl -X POST http://localhost:8080/render \
            -H "Content-Type: application/json" \
            -d '{"entry":"docs/index.adoc","formats":["html","pdf"]}' \
            2>/dev/null || true
    else
        # Start AsciiFrame in background for standalone mode
        ./asciiframe/asciiframe > /dev/null 2>&1 &
        ASCIIFRAME_PID=$!
        sleep 3
        curl -X POST http://localhost:8080/render \
            -H "Content-Type: application/json" \
            -d '{"entry":"docs/index.adoc","formats":["html","pdf"]}' \
            2>/dev/null || true
        # Stop AsciiFrame
        kill $ASCIIFRAME_PID 2>/dev/null || true
    fi
}

create_project_template() {
    echo "Creating project template..."
    
    mkdir -p docs
    
    if [[ ! -f "docs/index.adoc" ]]; then
        cat > docs/index.adoc << 'EOF'
= Welcome to AsciiFrame
Your Name
:toc:
:icons: font

== Getting Started

Congratulations! AsciiFrame is now installed and ready to use.

== Quick Commands

[source,bash]
----
# Start AsciiFrame server
asciiframe

# Generate docs
curl -X POST http://localhost:8080/render \
  -H "Content-Type: application/json" \
  -d '{"entry":"docs/index.adoc","formats":["html","pdf"]}'
----

== Next Steps

1. Visit http://localhost:8080/preview/index.html
2. Edit `docs/index.adoc`
3. Watch the magic happen!
EOF
    fi
}

print_final_instructions() {
    echo ""
    echo -e "${GREEN}🎉 AsciiFrame installation complete!${NC}"
    echo ""
    if [[ "$DOCKER_COMPOSE" == "true" ]]; then
        echo "🐳 Docker mode installed"
        echo "📁 Installation directory: $(pwd)"
        echo ""
        echo "🚀 To start AsciiFrame:"
        echo "   ./start.sh"
        echo ""
        echo "🛑 To stop AsciiFrame:"
        echo "   ./stop.sh"
    else
        echo "☕ Java mode installed"
        echo "📁 Installation directory: $INSTALL_DIR"
        echo ""
        echo "🚀 To start AsciiFrame:"
        echo "   asciiframe"
        echo ""
        echo "🧪 Test installation:"
        echo "   asciiframe --version || java -jar $INSTALL_DIR/asciiframe.jar --help"
    fi
    echo ""
    echo "📝 Edit your docs:"
    echo "   Edit files in ./docs/"
    echo ""
    echo "🌐 Access your docs:"
    echo "   http://localhost:8080/preview/index.html"
    echo ""
    echo "🔧 API endpoint:"
    echo "   POST http://localhost:8080/render"
    echo ""
    echo "📖 For more information:"
    echo "   $REPO_URL"
    echo ""
}

# Main installation flow
main() {
    check_dependencies
    create_install_directory
    
    if [[ "$DOCKER_COMPOSE" == "true" ]]; then
        install_docker_mode
    else
        install_standalone_mode
    fi
    
    # Only generate docs if --skip-start not provided
    if [[ "$1" != "--skip-start" ]]; then
        generate_initial_docs
    fi
    
    print_final_instructions
}

# Handle command line arguments
case "${1:-}" in
    --docker)
        DOCKER_COMPOSE="true"
        main "${@:2}"
        ;;
    --standalone)
        DOCKER_COMPOSE="false"
        main "${@:2}"
        ;;
    --global)
        GLOBAL_INSTALL="true"
        DOCKER_COMPOSE="false"
        main "${@:2}"
        ;;
    --help|-h)
        echo "AsciiFrame Professional Installer"
        echo ""
        echo "Usage: curl -sSL https://raw.githubusercontent.com/remixxx31/asciiframe-public/main/install.sh | bash -s -- [options]"
        echo ""
        echo "Options:"
        echo "  --docker      Force Docker installation"
        echo "  --standalone  Force standalone JAR installation"
        echo "  --global      Install globally (requires sudo)"
        echo "  --skip-start  Don't start service after install"
        echo "  --help        Show this help"
        echo ""
        echo "Environment variables:"
        echo "  INSTALL_DIR          Installation directory (default: ./asciiframe)"
        echo "  ASCIIFRAME_VERSION   Version to install (default: latest)"
        echo "  DOCKER_COMPOSE       Force Docker mode (true/false)"
        echo "  GLOBAL_INSTALL       Install globally (true/false)"
        echo ""
        echo "Examples:"
        echo "  curl -sSL https://raw.githubusercontent.com/remixxx31/asciiframe-public/main/install.sh | bash"
        echo "  curl -sSL https://raw.githubusercontent.com/remixxx31/asciiframe-public/main/install.sh | bash -s -- --docker"
        echo "  curl -sSL https://raw.githubusercontent.com/remixxx31/asciiframe-public/main/install.sh | INSTALL_DIR=/opt/asciiframe bash"
        ;;
    *)
        main "$@"
        ;;
esac