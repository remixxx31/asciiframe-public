#!/bin/bash
#
# Modified install script for testing environment
# Uses local JAR instead of downloading from releases
#

# Colors and utility functions (copied from install.sh)
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_header() {
    echo -e "${BLUE}"
    echo "🎯 AsciiFrame Test Installer"
    echo "======================================"
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

# Configuration
INSTALL_DIR=${INSTALL_DIR:-"./asciiframe"}
DOCKER_COMPOSE=${DOCKER_COMPOSE:-"false"}
GLOBAL_INSTALL=${GLOBAL_INSTALL:-"false"}

check_dependencies() {
    print_header
    echo "Test environment setup..."
    
    # Check for Java if standalone mode
    if [[ "$DOCKER_COMPOSE" != "true" ]]; then
        if ! command -v java &> /dev/null; then
            print_warning "Java not found. Will install Docker mode instead."
            DOCKER_COMPOSE="true"
        else
            print_success "Java found"
        fi
    fi
}

create_install_directory() {
    if [[ "$DOCKER_COMPOSE" == "true" ]]; then
        echo "Creating project directory: $INSTALL_DIR"
        mkdir -p "$INSTALL_DIR"
        cd "$INSTALL_DIR"
    else
        echo "Installing to: $INSTALL_DIR"
        mkdir -p "$INSTALL_DIR"
    fi
}

install_docker_mode() {
    echo "Docker mode installation (test mode)..."
    print_success "Docker mode test completed"
}

# Test-specific install_standalone_mode function
install_standalone_mode() {
    echo "Installing AsciiFrame in standalone mode (TEST MODE)..."
    
    # Check if fixtures JAR exists in container structure
    # Container structure: /home/testuser/fixtures/app-fat.jar
    LOCAL_JAR="/home/testuser/fixtures/app-fat.jar"
    if [[ ! -f "$LOCAL_JAR" ]]; then
        # Fallback to relative path
        LOCAL_JAR="./fixtures/app-fat.jar"
        if [[ ! -f "$LOCAL_JAR" ]]; then
            print_error "Test JAR not found at: $LOCAL_JAR or /home/testuser/fixtures/app-fat.jar"
            print_error "Available files in fixtures:"
            ls -la ./fixtures/ 2>/dev/null || ls -la /home/testuser/fixtures/ 2>/dev/null || echo "No fixtures directory found"
            return 1
        fi
    fi
    
    print_success "Using local test JAR: $LOCAL_JAR"
    
    mkdir -p "$INSTALL_DIR"
    
    # Copy JAR instead of downloading
    cp "$LOCAL_JAR" "$INSTALL_DIR/asciiframe.jar"
    print_success "JAR copied to installation directory"
    
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
    print_success "Wrapper script created"
    
    # Create config (simplified for testing)
    cat > config.yml << 'EOF'
# AsciiFrame Test Configuration
entry: docs/index.adoc
outDir: output
formats: [html]
theme:
  html: documentation
watch:
  enabled: false
server:
  port: 8080
EOF
    
    print_success "Configuration created"
    print_success "Standalone installation completed (TEST MODE)"
}

# No version check needed in test mode

# Always run in test mode when executed
print_header
echo "🧪 TEST MODE - Using local JAR files"
echo "======================================"

# Set test defaults
DOCKER_COMPOSE=${DOCKER_COMPOSE:-"false"}
GLOBAL_INSTALL=${GLOBAL_INSTALL:-"false"}

# Call the main functions
check_dependencies
create_install_directory

if [[ "$DOCKER_COMPOSE" == "true" ]]; then
    install_docker_mode
else
    install_standalone_mode
fi

print_success "Test installation completed!"