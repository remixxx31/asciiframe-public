#!/bin/bash
#
# Multi-Platform Installation Test Runner
# Runs installation tests across different operating systems and environments
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
TEST_PLATFORMS=${TEST_PLATFORMS:-"ubuntu alpine centos"}
PARALLEL_TESTS=${PARALLEL_TESTS:-"true"}
KEEP_CONTAINERS=${KEEP_CONTAINERS:-"false"}
TEST_RESULTS_DIR="$PROJECT_ROOT/testing/results/$(date +%Y%m%d_%H%M%S)"

print_header() {
    echo -e "${BLUE}"
    echo "🌍 AsciiFrame Multi-Platform Installation Tests"
    echo "==============================================="
    echo "Test Platforms: $TEST_PLATFORMS"
    echo "Parallel Tests: $PARALLEL_TESTS"
    echo "Results Dir: $TEST_RESULTS_DIR"
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

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

setup_test_environment() {
    print_info "Setting up test environment..."
    
    # Create results directory
    mkdir -p "$TEST_RESULTS_DIR"
    
    # Copy test files to results directory for reference
    cp -r "$SCRIPT_DIR"/../ "$TEST_RESULTS_DIR/test-suite"
    
    print_success "Test environment ready"
}

build_test_image() {
    local platform="$1"
    local dockerfile="$SCRIPT_DIR/../docker/Dockerfile.$platform"
    local image_name="asciiframe-test:$platform"
    
    print_info "Building test image for $platform..."
    
    if [[ ! -f "$dockerfile" ]]; then
        print_error "Dockerfile not found: $dockerfile"
        return 1
    fi
    
    # Copy scripts and fixtures for Docker build
    local build_context="$TEST_RESULTS_DIR/build-$platform"
    mkdir -p "$build_context"
    cp -r "$SCRIPT_DIR" "$build_context/"
    cp -r "$SCRIPT_DIR/../fixtures" "$build_context/" 2>/dev/null || mkdir -p "$build_context/fixtures"
    
    # Build image
    if docker build -f "$dockerfile" -t "$image_name" "$build_context" > "$TEST_RESULTS_DIR/build-$platform.log" 2>&1; then
        print_success "Built test image: $image_name"
        return 0
    else
        print_error "Failed to build test image for $platform"
        return 1
    fi
}

run_platform_test() {
    local platform="$1"
    local image_name="asciiframe-test:$platform"
    local container_name="asciiframe-test-$platform-$$"
    local log_file="$TEST_RESULTS_DIR/test-$platform.log"
    local result_file="$TEST_RESULTS_DIR/result-$platform.txt"
    
    print_info "Running installation test on $platform..."
    
    # Run test container
    local exit_code=0
    if docker run --name "$container_name" \
        --privileged \
        -v /var/run/docker.sock:/var/run/docker.sock \
        -e SKIP_CLEANUP=true \
        "$image_name" > "$log_file" 2>&1; then
        echo "SUCCESS" > "$result_file"
        print_success "Installation test passed on $platform"
    else
        exit_code=$?
        echo "FAILED" > "$result_file"
        echo "$exit_code" >> "$result_file"
        print_error "Installation test failed on $platform (exit code: $exit_code)"
    fi
    
    # Copy test artifacts from container
    local artifacts_dir="$TEST_RESULTS_DIR/artifacts-$platform"
    mkdir -p "$artifacts_dir"
    
    if docker cp "$container_name:/home/testuser/" "$artifacts_dir/" 2>/dev/null; then
        print_info "Copied test artifacts for $platform"
    fi
    
    # Cleanup container (unless keeping for debugging)
    if [[ "$KEEP_CONTAINERS" != "true" ]]; then
        docker rm "$container_name" > /dev/null 2>&1 || true
    else
        print_info "Container kept for debugging: $container_name"
    fi
    
    return $exit_code
}

run_sequential_tests() {
    print_info "Running tests sequentially..."
    
    local failed_platforms=()
    
    for platform in $TEST_PLATFORMS; do
        if build_test_image "$platform"; then
            if ! run_platform_test "$platform"; then
                failed_platforms+=("$platform")
            fi
        else
            failed_platforms+=("$platform")
        fi
    done
    
    return ${#failed_platforms[@]}
}

run_parallel_tests() {
    print_info "Running tests in parallel..."
    
    local pids=()
    local failed_platforms=()
    
    # Build all images first
    for platform in $TEST_PLATFORMS; do
        if ! build_test_image "$platform"; then
            failed_platforms+=("$platform")
        fi
    done
    
    # Run tests in parallel
    for platform in $TEST_PLATFORMS; do
        if [[ ! " ${failed_platforms[@]} " =~ " ${platform} " ]]; then
            run_platform_test "$platform" &
            pids+=($!)
        fi
    done
    
    # Wait for all tests to complete
    local overall_exit_code=0
    for pid in "${pids[@]}"; do
        if ! wait "$pid"; then
            overall_exit_code=1
        fi
    done
    
    return $overall_exit_code
}

generate_test_report() {
    print_info "Generating test report..."
    
    local report_file="$TEST_RESULTS_DIR/TEST_REPORT.md"
    
    cat > "$report_file" << EOF
# AsciiFrame Installation Test Report

**Test Date:** $(date)  
**Test Platforms:** $TEST_PLATFORMS  
**Test Mode:** $([ "$PARALLEL_TESTS" = "true" ] && echo "Parallel" || echo "Sequential")

## Results Summary

| Platform | Result | Details |
|----------|--------|---------|
EOF
    
    for platform in $TEST_PLATFORMS; do
        local result_file="$TEST_RESULTS_DIR/result-$platform.txt"
        local log_file="$TEST_RESULTS_DIR/test-$platform.log"
        
        if [[ -f "$result_file" ]]; then
            local result=$(head -n1 "$result_file")
            local details="[Log](test-$platform.log)"
            
            if [[ "$result" == "SUCCESS" ]]; then
                echo "| $platform | ✅ PASSED | $details |" >> "$report_file"
            else
                local exit_code=$(tail -n1 "$result_file" 2>/dev/null || echo "unknown")
                echo "| $platform | ❌ FAILED (exit: $exit_code) | $details |" >> "$report_file"
            fi
        else
            echo "| $platform | ❓ NO RESULT | Build failed |" >> "$report_file"
        fi
    done
    
    cat >> "$report_file" << EOF

## Test Environment

- Docker Version: $(docker --version 2>/dev/null || echo "Not available")
- Host OS: $(uname -a)
- Test Suite Location: \`$TEST_RESULTS_DIR\`

## Log Files

EOF
    
    for platform in $TEST_PLATFORMS; do
        echo "- [$platform Test Log](test-$platform.log)" >> "$report_file"
        echo "- [$platform Build Log](build-$platform.log)" >> "$report_file"
    done
    
    cat >> "$report_file" << EOF

## Artifacts

Test artifacts are available in the \`artifacts-*\` directories for each platform.

EOF
    
    print_success "Test report generated: $report_file"
}

print_final_results() {
    local failed_count=0
    local passed_count=0
    
    echo ""
    print_info "=== FINAL RESULTS ==="
    
    for platform in $TEST_PLATFORMS; do
        local result_file="$TEST_RESULTS_DIR/result-$platform.txt"
        
        if [[ -f "$result_file" ]]; then
            local result=$(head -n1 "$result_file")
            if [[ "$result" == "SUCCESS" ]]; then
                print_success "$platform: PASSED"
                ((passed_count++))
            else
                print_error "$platform: FAILED"
                ((failed_count++))
            fi
        else
            print_error "$platform: NO RESULT"
            ((failed_count++))
        fi
    done
    
    echo ""
    if [[ $failed_count -eq 0 ]]; then
        print_success "🎉 All platforms passed! ($passed_count/$((passed_count + failed_count)))"
    else
        print_error "❌ $failed_count platform(s) failed, $passed_count passed"
    fi
    
    echo ""
    print_info "📁 Full results available at: $TEST_RESULTS_DIR"
    print_info "📊 Test report: $TEST_RESULTS_DIR/TEST_REPORT.md"
    
    return $failed_count
}

cleanup() {
    if [[ "$KEEP_CONTAINERS" != "true" ]]; then
        print_info "Cleaning up test images..."
        for platform in $TEST_PLATFORMS; do
            docker rmi "asciiframe-test:$platform" > /dev/null 2>&1 || true
        done
    fi
}

# Main execution
main() {
    print_header
    
    # Check Docker availability
    if ! command -v docker &> /dev/null; then
        print_error "Docker is required but not installed"
        exit 1
    fi
    
    # Setup
    setup_test_environment
    
    # Run tests
    local exit_code=0
    if [[ "$PARALLEL_TESTS" == "true" ]]; then
        if ! run_parallel_tests; then
            exit_code=1
        fi
    else
        if ! run_sequential_tests; then
            exit_code=1
        fi
    fi
    
    # Generate report
    generate_test_report
    
    # Show results
    if ! print_final_results; then
        exit_code=1
    fi
    
    # Cleanup
    trap cleanup EXIT
    
    exit $exit_code
}

# Handle command line arguments
case "${1:-}" in
    --help|-h)
        echo "AsciiFrame Multi-Platform Installation Test Runner"
        echo ""
        echo "Usage: $0 [options]"
        echo ""
        echo "Options:"
        echo "  --sequential         Run tests sequentially (default: parallel)"
        echo "  --keep-containers    Keep test containers for debugging"
        echo "  --ubuntu-only        Test only Ubuntu"
        echo "  --alpine-only        Test only Alpine Linux"
        echo "  --centos-only        Test only CentOS"
        echo "  --help               Show this help"
        echo ""
        echo "Environment variables:"
        echo "  TEST_PLATFORMS       Space-separated list of platforms"
        echo "                       (default: 'ubuntu alpine centos')"
        echo "  PARALLEL_TESTS       Set to 'false' for sequential execution"
        echo "  KEEP_CONTAINERS      Set to 'true' to keep containers"
        echo ""
        ;;
    --sequential)
        PARALLEL_TESTS="false"
        main
        ;;
    --keep-containers)
        KEEP_CONTAINERS="true"
        main
        ;;
    --ubuntu-only)
        TEST_PLATFORMS="ubuntu"
        main
        ;;
    --alpine-only)
        TEST_PLATFORMS="alpine"
        main
        ;;
    --centos-only)
        TEST_PLATFORMS="centos"
        main
        ;;
    *)
        main "$@"
        ;;
esac