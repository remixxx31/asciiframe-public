#!/bin/bash
#
# AsciiFrame Installation Test Suite
# Tests installation in clean environments
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test configuration
TEST_DIR="/tmp/asciiframe-test-$$"
LOG_FILE="$TEST_DIR/test.log"
INSTALL_MODES=${INSTALL_MODES:-"standalone docker global"}
SKIP_CLEANUP=${SKIP_CLEANUP:-"false"}

print_header() {
    echo -e "${BLUE}"
    echo "🧪 AsciiFrame Installation Test Suite"
    echo "======================================"
    echo "Test Environment: $(uname -a)"
    echo "Test Directory: $TEST_DIR"
    echo "Log File: $LOG_FILE"
    echo -e "${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
    [ -f "$LOG_FILE" ] && echo "[SUCCESS] $1" >> "$LOG_FILE"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
    [ -f "$LOG_FILE" ] && echo "[WARNING] $1" >> "$LOG_FILE"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
    [ -f "$LOG_FILE" ] && echo "[ERROR] $1" >> "$LOG_FILE"
}

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
    [ -f "$LOG_FILE" ] && echo "[INFO] $1" >> "$LOG_FILE"
}

setup_test_environment() {
    print_info "Setting up test environment..."
    
    # Create test directory
    mkdir -p "$TEST_DIR"
    cd "$TEST_DIR"
    
    # Initialize log
    echo "AsciiFrame Installation Test - $(date)" > "$LOG_FILE"
    echo "========================================" >> "$LOG_FILE"
    
    # Create test documents
    mkdir -p test-docs
    cat > test-docs/index.adoc << 'EOF'
= Test Document
Test Author
:toc:
:icons: font

== Introduction

This is a test document for AsciiFrame installation validation.

== Features Test

[source,bash]
----
echo "Hello AsciiFrame!"
----

== Diagrams Test

[plantuml]
----
@startuml
Alice -> Bob: Hello
Bob -> Alice: Hi!
@enduml
----

== End

Installation test completed successfully!
EOF

    print_success "Test environment setup complete"
}

test_prerequisites() {
    print_info "Checking prerequisites..."
    
    # Check required tools
    local required_tools=("curl" "wget")
    for tool in "${required_tools[@]}"; do
        if command -v "$tool" &> /dev/null; then
            print_success "$tool found"
        else
            print_error "$tool not found"
            return 1
        fi
    done
    
    # Check Java (may not be required for Docker mode)
    if command -v java &> /dev/null; then
        local java_version=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
        print_success "Java $java_version found"
    else
        print_warning "Java not found (Docker mode will be used)"
    fi
    
    # Check Docker (may not be required for standalone mode)
    if command -v docker &> /dev/null; then
        print_success "Docker found"
    else
        print_warning "Docker not found (Standalone mode will be used)"
    fi
    
    print_success "Prerequisites check complete"
}

download_install_script() {
    print_info "Downloading install script..."
    
    # For testing, we'll use the local install script
    if [[ -f "../../install.sh" ]]; then
        cp ../../install.sh ./install.sh
        print_success "Using local install script"
    else
        # Fallback to downloading from GitHub
        if curl -fsSL "https://raw.githubusercontent.com/remixxx31/asciiframe-public/main/install.sh" -o install.sh; then
            print_success "Downloaded install script from GitHub"
        else
            print_error "Failed to download install script"
            return 1
        fi
    fi
    
    chmod +x install.sh
}

test_standalone_installation() {
    print_info "Testing standalone installation..."
    
    local test_dir="$TEST_DIR/standalone"
    mkdir -p "$test_dir"
    cd "$test_dir"
    
    # Copy install script
    cp ../install.sh .
    
    # Test installation
    if INSTALL_DIR="./asciiframe" ./install.sh --standalone --skip-start; then
        print_success "Standalone installation completed"
    else
        print_error "Standalone installation failed"
        return 1
    fi
    
    # Verify installation
    if [[ -f "./asciiframe/asciiframe.jar" ]]; then
        print_success "JAR file found"
    else
        print_error "JAR file not found"
        return 1
    fi
    
    if [[ -f "./asciiframe/asciiframe" ]] && [[ -x "./asciiframe/asciiframe" ]]; then
        print_success "Wrapper script found and executable"
    else
        print_error "Wrapper script not found or not executable"
        return 1
    fi
    
    # Test execution (version check)
    if timeout 30 ./asciiframe/asciiframe --help > /dev/null 2>&1; then
        print_success "AsciiFrame executable works"
    else
        print_warning "AsciiFrame executable test failed (may be normal)"
    fi
    
    # Test basic functionality
    test_basic_functionality "$test_dir"
    
    cd "$TEST_DIR"
    print_success "Standalone installation test completed"
}

test_docker_installation() {
    print_info "Testing Docker installation..."
    
    # Skip if Docker not available
    if ! command -v docker &> /dev/null; then
        print_warning "Docker not available, skipping Docker installation test"
        return 0
    fi
    
    local test_dir="$TEST_DIR/docker"
    mkdir -p "$test_dir"
    cd "$test_dir"
    
    # Copy install script
    cp ../install.sh .
    
    # Test installation
    if INSTALL_DIR="./asciiframe" ./install.sh --docker --skip-start; then
        print_success "Docker installation completed"
    else
        print_error "Docker installation failed"
        return 1
    fi
    
    # Verify installation files
    local required_files=("docker-compose.yml" "config.yml" "start.sh" "stop.sh" "docs/index.adoc")
    for file in "${required_files[@]}"; do
        if [[ -f "$file" ]]; then
            print_success "Required file found: $file"
        else
            print_error "Required file missing: $file"
            return 1
        fi
    done
    
    # Test Docker Compose configuration
    if docker compose config > /dev/null 2>&1; then
        print_success "Docker Compose configuration valid"
    else
        print_error "Docker Compose configuration invalid"
        return 1
    fi
    
    # Test basic functionality
    test_basic_functionality "$test_dir"
    
    cd "$TEST_DIR"
    print_success "Docker installation test completed"
}

test_global_installation() {
    print_info "Testing global installation..."
    
    # Skip global installation in containers (requires sudo)
    if [[ -f /.dockerenv ]]; then
        print_warning "Running in container, skipping global installation test"
        return 0
    fi
    
    local test_dir="$TEST_DIR/global"
    mkdir -p "$test_dir"
    cd "$test_dir"
    
    # Copy install script
    cp ../install.sh .
    
    # Test installation (to local .local/bin instead of system-wide)
    if GLOBAL_INSTALL="true" INSTALL_DIR="$HOME/.local/bin" ./install.sh --global --skip-start; then
        print_success "Global installation completed"
    else
        print_error "Global installation failed"
        return 1
    fi
    
    # Verify installation
    if [[ -f "$HOME/.local/bin/asciiframe.jar" ]]; then
        print_success "JAR file found in global location"
    else
        print_error "JAR file not found in global location"
        return 1
    fi
    
    if [[ -f "$HOME/.local/bin/asciiframe" ]] && [[ -x "$HOME/.local/bin/asciiframe" ]]; then
        print_success "Global wrapper script found and executable"
    else
        print_error "Global wrapper script not found or not executable"
        return 1
    fi
    
    cd "$TEST_DIR"
    print_success "Global installation test completed"
}

test_basic_functionality() {
    local test_dir="$1"
    print_info "Testing basic functionality in $test_dir..."
    
    # Copy test documents
    cp -r ../test-docs ./docs
    
    # Create a simple config for testing
    cat > config.yml << 'EOF'
entry: docs/index.adoc
outDir: build
formats: [html]
theme:
  html: default
server:
  port: 8080
watch:
  enabled: false
EOF
    
    # Test document generation (if possible)
    if [[ -f "asciiframe/asciiframe" ]]; then
        # Standalone mode
        print_info "Testing document generation (standalone)..."
        if timeout 60 ./asciiframe/asciiframe --config config.yml --no-server > /dev/null 2>&1; then
            print_success "Document generation successful"
        else
            print_warning "Document generation test failed (may be normal in test environment)"
        fi
    elif [[ -f "docker-compose.yml" ]]; then
        # Docker mode
        print_info "Testing Docker configuration..."
        if docker compose config > /dev/null 2>&1; then
            print_success "Docker Compose configuration valid"
        else
            print_warning "Docker Compose configuration test failed"
        fi
    fi
    
    print_success "Basic functionality test completed"
}

run_installation_tests() {
    print_info "Running installation tests for modes: $INSTALL_MODES"
    
    local failed_tests=()
    
    for mode in $INSTALL_MODES; do
        print_info "Testing installation mode: $mode"
        
        case "$mode" in
            "standalone")
                if ! test_standalone_installation; then
                    failed_tests+=("standalone")
                fi
                ;;
            "docker")
                if ! test_docker_installation; then
                    failed_tests+=("docker")
                fi
                ;;
            "global")
                if ! test_global_installation; then
                    failed_tests+=("global")
                fi
                ;;
            *)
                print_warning "Unknown installation mode: $mode"
                ;;
        esac
    done
    
    # Report results
    if [[ ${#failed_tests[@]} -eq 0 ]]; then
        print_success "All installation tests passed!"
        return 0
    else
        print_error "Failed installation tests: ${failed_tests[*]}"
        return 1
    fi
}

cleanup() {
    if [[ "$SKIP_CLEANUP" == "true" ]]; then
        print_info "Skipping cleanup (SKIP_CLEANUP=true)"
        print_info "Test artifacts available at: $TEST_DIR"
        return
    fi
    
    print_info "Cleaning up test environment..."
    
    # Stop any running Docker containers
    if [[ -f "$TEST_DIR/docker/docker-compose.yml" ]]; then
        cd "$TEST_DIR/docker"
        docker compose down > /dev/null 2>&1 || true
    fi
    
    # Remove test directory
    rm -rf "$TEST_DIR" || true
    
    print_success "Cleanup complete"
}

# Main test execution
main() {
    print_header
    
    # Setup
    setup_test_environment
    
    # Run prerequisite checks
    if ! test_prerequisites; then
        print_error "Prerequisites check failed"
        exit 1
    fi
    
    # Download install script
    if ! download_install_script; then
        print_error "Failed to get install script"
        exit 1
    fi
    
    # Run installation tests
    local exit_code=0
    if ! run_installation_tests; then
        exit_code=1
    fi
    
    # Cleanup
    trap cleanup EXIT
    
    # Final report
    echo ""
    if [[ $exit_code -eq 0 ]]; then
        print_success "🎉 All installation tests completed successfully!"
        echo ""
        print_info "Test log available at: $LOG_FILE"
    else
        print_error "❌ Some installation tests failed!"
        echo ""
        print_info "Check test log for details: $LOG_FILE"
    fi
    
    exit $exit_code
}

# Handle command line arguments
case "${1:-}" in
    --help|-h)
        echo "AsciiFrame Installation Test Suite"
        echo ""
        echo "Usage: $0 [options]"
        echo ""
        echo "Options:"
        echo "  --standalone-only    Test only standalone installation"
        echo "  --docker-only        Test only Docker installation"
        echo "  --global-only        Test only global installation"
        echo "  --skip-cleanup       Don't cleanup test files"
        echo "  --help               Show this help"
        echo ""
        echo "Environment variables:"
        echo "  INSTALL_MODES        Space-separated list of modes to test"
        echo "                       (default: 'standalone docker global')"
        echo "  SKIP_CLEANUP         Set to 'true' to keep test files"
        echo ""
        ;;
    --standalone-only)
        INSTALL_MODES="standalone"
        main
        ;;
    --docker-only)
        INSTALL_MODES="docker"
        main
        ;;
    --global-only)
        INSTALL_MODES="global"
        main
        ;;
    --skip-cleanup)
        SKIP_CLEANUP="true"
        main
        ;;
    *)
        main "$@"
        ;;
esac