FROM python:3.11-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements and install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip setuptools wheel && \
    pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY app.py .

# Default: run Celery worker
# Override ENTRYPOINT to run FastAPI server instead
CMD ["celery", "-A", "app.celery_app", "worker", "-l", "info", "-c", "4"]
