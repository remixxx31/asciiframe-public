#!/bin/bash
#
# Quick Installation Test
# Fast test for development/local validation
#

set -e

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

print_success() { echo -e "${GREEN}✅ $1${NC}"; }
print_error() { echo -e "${RED}❌ $1${NC}"; }
print_info() { echo -e "${BLUE}ℹ️  $1${NC}"; }

# Quick test configuration
TEST_DIR="/tmp/asciiframe-quick-test-$$"
PLATFORM=${PLATFORM:-"ubuntu"}

print_info "🚀 AsciiFrame Quick Installation Test"
print_info "Platform: $PLATFORM"
print_info "Test Dir: $TEST_DIR"

# Setup
mkdir -p "$TEST_DIR"
cd "$TEST_DIR"

# Build test image
print_info "Building test image..."
DOCKERFILE="../testing/installation/docker/Dockerfile.$PLATFORM"

if [[ ! -f "$DOCKERFILE" ]]; then
    print_error "Dockerfile not found: $DOCKERFILE"
    exit 1
fi

# Copy scripts for build context
cp -r ../testing/installation/scripts ./
mkdir -p fixtures

if docker build -f "$DOCKERFILE" -t "asciiframe-quick-test:$PLATFORM" . > build.log 2>&1; then
    print_success "Test image built"
else
    print_error "Failed to build test image"
    cat build.log
    exit 1
fi

# Run quick test
print_info "Running installation test..."
if docker run --rm \
    --privileged \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -e INSTALL_MODES="standalone" \
    -e SKIP_CLEANUP=true \
    "asciiframe-quick-test:$PLATFORM" > test.log 2>&1; then
    print_success "Installation test passed!"
else
    print_error "Installation test failed!"
    echo ""
    print_info "Last 20 lines of test log:"
    tail -20 test.log
    exit 1
fi

# Cleanup
cd ..
rm -rf "$TEST_DIR"
docker rmi "asciiframe-quick-test:$PLATFORM" > /dev/null 2>&1 || true

print_success "🎉 Quick test completed successfully!"