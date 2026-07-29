#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🚀 OpenTron Benchmark Suite"
echo "============================"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_docker() {
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed. Please install Docker first."
        exit 1
    fi
    log_info "Docker found: $(docker --version)"
}

check_docker_compose() {
    if ! command -v docker-compose &> /dev/null; then
        log_error "docker-compose is not installed. Please install it first."
        exit 1
    fi
    log_info "docker-compose found: $(docker-compose --version)"
}

check_java() {
    if ! command -v java &> /dev/null; then
        log_warn "Java 21 not found in PATH"
        return 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | grep "version" | head -1)
    log_info "Java found: $JAVA_VERSION"
    return 0
}

setup_java() {
    # Check common Java 21 locations
    JAVA_HOMES=(
        "/usr/libexec/java_home"
        "/opt/java/jdk-21"
        "$HOME/.jdks/jdk-21"
        "$HOME/jdk-21"
    )
    
    for JAVA_HOME_CMD in "${JAVA_HOMES[@]}"; do
        if [ -d "$JAVA_HOME_CMD" ]; then
            export JAVA_HOME="$JAVA_HOME_CMD"
            export PATH="$JAVA_HOME/bin:$PATH"
            log_info "Java Home set to: $JAVA_HOME"
            return 0
        fi
    done
    
    # Try using /usr/libexec/java_home on macOS
    if command -v /usr/libexec/java_home &> /dev/null; then
        export JAVA_HOME=$(/usr/libexec/java_home -v 21)
        export PATH="$JAVA_HOME/bin:$PATH"
        log_info "Java Home set to: $JAVA_HOME"
        return 0
    fi
    
    log_error "Java 21 not found. Please set JAVA_HOME manually."
    return 1
}

start_services() {
    log_info "Starting benchmark services..."
    log_info "Pulling latest images..."
    docker-compose pull
    
    log_info "Building images (first run may take 5+ minutes)..."
    docker-compose build
    
    log_info "Starting services..."
    docker-compose up -d
    
    log_info "Waiting for services to become healthy..."
    local max_attempts=60
    local attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        if docker-compose ps | grep -E "(opentron|python-api)" | grep -q "Up"; then
            log_info "Services are running!"
            return 0
        fi
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    log_warn "Services may not be fully healthy yet. Check with: docker-compose logs"
    return 0
}

run_benchmarks() {
    log_info "Running benchmark suite..."
    
    log_info "Waiting 30 seconds for services to fully stabilize..."
    sleep 30
    
    log_info "Benchmarks running automatically in load-generator container..."
    docker-compose logs -f load-generator 2>/dev/null | while IFS= read -r line; do
        echo "$line"
        if echo "$line" | grep -q "Results saved"; then
            break
        fi
    done
    
    log_info "Benchmark run complete!"
}

generate_report() {
    if [ -f "benchmark_results.json" ]; then
        log_info "Generating benchmark report..."
        python3 analyze_results.py benchmark_results.json
        
        if [ -f "BENCHMARK_REPORT.md" ]; then
            log_info "Report generated: BENCHMARK_REPORT.md"
            echo ""
            echo "To view the report:"
            echo "  cat BENCHMARK_REPORT.md"
        fi
    else
        log_warn "benchmark_results.json not found. Skipping report generation."
    fi
}

show_dashboards() {
    echo ""
    echo "📊 Monitoring Dashboards"
    echo "========================"
    echo ""
    echo "Grafana:"
    echo "  URL: http://localhost:3000"
    echo "  User: admin"
    echo "  Password: admin"
    echo ""
    echo "Prometheus:"
    echo "  URL: http://localhost:9090"
    echo ""
}

show_help() {
    cat << EOF
Usage: $0 [COMMAND]

Commands:
  start       Start benchmark services (default)
  run         Run benchmarks (assumes services are already running)
  report      Generate report from results
  stop        Stop all services
  logs        Show service logs
  status      Show service status
  clean       Stop services and remove volumes
  help        Show this help message

Examples:
  ./benchmark.sh                # Start services and run benchmarks
  ./benchmark.sh run            # Run benchmarks on existing services
  ./benchmark.sh report         # Generate report from previous run
  ./benchmark.sh stop           # Stop all services
  ./benchmark.sh clean          # Clean up everything

EOF
}

# Main
case "${1:-start}" in
    start)
        check_docker
        check_docker_compose
        if ! check_java; then
            log_warn "Java 21 setup..."
            if ! setup_java; then
                log_error "Failed to setup Java. Please set JAVA_HOME manually."
                exit 1
            fi
        fi
        start_services
        run_benchmarks
        generate_report
        show_dashboards
        ;;
    run)
        run_benchmarks
        generate_report
        ;;
    report)
        generate_report
        ;;
    stop)
        log_info "Stopping services..."
        docker-compose down
        log_info "Services stopped"
        ;;
    logs)
        docker-compose logs -f
        ;;
    status)
        docker-compose ps
        ;;
    clean)
        log_info "Removing services and volumes..."
        docker-compose down -v
        rm -f benchmark_results.json BENCHMARK_REPORT.md
        log_info "Cleanup complete"
        ;;
    help)
        show_help
        ;;
    *)
        log_error "Unknown command: $1"
        show_help
        exit 1
        ;;
esac
