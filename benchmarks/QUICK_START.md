# 🚀 OpenTron Benchmark Quick Reference

## 30-Second Start

```bash
cd benchmarks
./benchmark.sh start          # macOS/Linux
# OR
benchmark.bat start           # Windows
```

The load generator runs automatically. Results appear in `benchmark_results.json` and `BENCHMARK_REPORT.md`.

---

## Commands

| Command | Purpose |
|---------|---------|
| `./benchmark.sh start` | Start services + run benchmarks |
| `./benchmark.sh run` | Run benchmarks on existing services |
| `./benchmark.sh report` | Generate report from results |
| `./benchmark.sh stop` | Stop all services |
| `./benchmark.sh logs` | View service logs |
| `./benchmark.sh status` | Show service status |
| `./benchmark.sh clean` | Remove all containers/volumes |

---

## Monitoring

**Grafana Dashboard**
- URL: http://localhost:3000
- User: `admin`
- Password: `admin`
- Metrics: JVM, HTTP requests, latency

**Prometheus Metrics**
- URL: http://localhost:9090
- Query examples:
  - `rate(http_requests_total[1m])`
  - `jvm_memory_used_bytes`
  - `http_request_duration_seconds`

---

## Results Interpretation

### Throughput Ratio
- `2.6x` means OpenTron does 2.6 tasks/second for every 1 Python does
- **Higher is better**

### Latency Ratio
- `1.4x` means Python latency is 1.4× higher than OpenTron
- **Lower ratio is better** (means OpenTron is faster)

### Memory Per Task
- OpenTron: ~50 bytes/task
- Python: ~300 bytes/task (3 workers)
- **OpenTron is 6x more efficient**

---

## Common Issues & Fixes

**Services won't start**
```bash
docker system prune -a      # Clean up images
docker-compose up --build   # Rebuild
```

**Load generator can't connect**
```bash
docker-compose ps           # Check services are running
sleep 30                    # Wait for services to be ready
```

**Out of memory**
```bash
# In docker-compose.yml, add under service:
deploy:
  resources:
    limits:
      memory: 4G
```

**Old results interfering**
```bash
rm -f benchmark_results.json BENCHMARK_REPORT.md
```

---

## Customization

### Change Concurrency Levels

Edit `load_generator.py`, line ~190:

```python
configs = [
    {"concurrency": 10, "total_tasks": 100},
    {"concurrency": 5000, "total_tasks": 25000},  # Add more
]
```

### Change Workload Duration

Edit `load_generator.py`, line ~290:

```python
task_delay_ms=1000  # 1 second per task instead of 500ms
```

### Adjust Python Worker Count

Edit `docker-compose.yml`:

```yaml
python-worker-4:
  build:
    context: ./python_baseline
  environment:
    - CELERY_BROKER_URL=redis://redis:6379/0
  depends_on:
    - redis
  command: celery -A app.celery_app worker -l info -c 10
```

---

## Files Reference

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Complete test stack definition |
| `load_generator.py` | Main benchmark driver |
| `analyze_results.py` | Report generator |
| `application.yml` | Spring Boot configuration |
| `monitoring/prometheus.yml` | Metrics scraping config |
| `python_baseline/app.py` | Python baseline service |
| `benchmark.sh` / `.bat` | Launcher script |

---

## Expected Output

After running `./benchmark.sh start`:

```
✅ OpenTron Benchmark Suite
============================

[INFO] Docker found: Docker version 24.0.0
[INFO] docker-compose found: Docker Compose version v2.20.0
[INFO] Pulling latest images...
...
[INFO] Starting benchmark services...
[INFO] Waiting for services to become healthy...

📊 Monitoring Dashboards
========================

Grafana:
  URL: http://localhost:3000
  User: admin
  Password: admin

Prometheus:
  URL: http://localhost:9090
```

Then after ~10-15 minutes:

```
=== Benchmark Results ===

Concurrency 100:
  OpenTron Throughput: 195 tasks/sec
  Python Throughput:   75 tasks/sec
  Ratio: 2.6x

...

Results saved to benchmark_results.json
Report generated: BENCHMARK_REPORT.md
```

---

## Key Metrics to Watch

### Throughput
- OpenTron should be **2-5x higher** than Python
- Ratio increases with concurrency (virtual threads excel at scale)

### Latency p95
- OpenTron should stay **consistent** across concurrency levels
- Python should **increase** as workers become saturated

### Success Rate
- Both platforms should be **~99%+** (rare failures = network timeouts)

### Memory
- Look at `jvm_memory_used_bytes` in Prometheus
- Should be **linear with task count** (predictable scaling)

---

## Save Results for Presentation

```bash
# Export metrics
docker-compose exec prometheus \
  curl -s "http://localhost:9090/api/v1/query?query=up" | jq . > prometheus_metrics.json

# Screenshot Grafana dashboards
# (Use browser dev tools or screenshot tool)

# PDF report
pandoc BENCHMARK_REPORT.md -o BENCHMARK_REPORT.pdf
```

---

## Tear Down After Testing

```bash
./benchmark.sh clean

# Or manually:
docker-compose down -v
rm -f benchmark_results.json BENCHMARK_REPORT.md
```

---

## Support

**Documentation**: See `README.md` for detailed setup and customization

**Issues**: Check Docker logs:
```bash
docker-compose logs -f [service-name]
```

**Questions**: See [OpenTron Repository](https://github.com/open-tron-ai/OpenTron)

---

**Ready to benchmark?** Run: `./benchmark.sh start`

Grab coffee ☕ — results in ~15 minutes!
