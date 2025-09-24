#!/bin/bash
#
# Modified install script for testing environment
# Uses local JAR instead of downloading from releases
#

# Source the original install script functions
source "$(dirname "$0")/../../install.sh"

# Override the install_standalone_mode function to use local JAR
install_standalone_mode() {
    echo "Installing AsciiFrame in standalone mode (TEST MODE)..."
    
    # Check if fixtures JAR exists
    LOCAL_JAR="$(dirname "$0")/../fixtures/app-fat.jar"
    if [[ ! -f "$LOCAL_JAR" ]]; then
        print_error "Test JAR not found at: $LOCAL_JAR"
        print_error "Make sure the JAR is provided by the CI workflow"
        return 1
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

# Override the download function to prevent GitHub API calls in tests
get_latest_version() {
    ASCIIFRAME_VERSION="test-version"
    print_success "Using test version"
}

# Check if this script is being sourced or executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    # Being executed directly - run with test mode
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
fi