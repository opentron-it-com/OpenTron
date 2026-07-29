"""
FastAPI + Celery + Redis Baseline for OpenTron Benchmark Comparison

This reproduces the traditional Python AI agent stack:
- FastAPI for HTTP API
- Celery for async task queue
- Redis for task broker
- Single process model (scales with worker count)
"""

from fastapi import FastAPI, BackgroundTasks
from celery import Celery
import time
import asyncio
import os
import logging
from typing import List, Dict, Any
from datetime import datetime
import json

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# FastAPI app
app = FastAPI(title="Python AI Agent Baseline")

# Celery configuration
celery_app = Celery(
    "python_baseline",
    broker=os.getenv("CELERY_BROKER_URL", "redis://localhost:6379/0"),
    backend=os.getenv("CELERY_RESULT_BACKEND", "redis://localhost:6379/0"),
)

celery_app.conf.update(
    task_serializer="json",
    accept_content=["json"],
    result_serializer="json",
    timezone="UTC",
    enable_utc=True,
    task_track_started=True,
    worker_prefetch_multiplier=1,  # Simulate concurrency constraints
)


@celery_app.task(bind=True, name="agent.query_llm")
def query_llm_task(self, prompt: str, delay_ms: int = 500) -> Dict[str, Any]:
    """
    Simulate LLM query. Each task consumes one OS thread from Celery worker pool.
    With N tasks, you need N worker processes to handle concurrency.
    """
    try:
        logger.info(f"Task {self.request.id} starting LLM query")
        
        # Simulate I/O wait (network call to LLM API)
        time.sleep(delay_ms / 1000.0)
        
        response = {
            "status": "completed",
            "response": f"Response to: {prompt}",
            "task_id": str(self.request.id),
            "timestamp": datetime.utcnow().isoformat(),
        }
        logger.info(f"Task {self.request.id} completed")
        return response
    except Exception as e:
        logger.error(f"Task {self.request.id} failed: {e}")
        return {"status": "error", "error": str(e)}


@app.post("/v1/agents/query")
async def submit_agent_query(
    prompt: str,
    delay_ms: int = 500,
    background_tasks: BackgroundTasks = None
) -> Dict[str, Any]:
    """
    Submit agent query to Celery task queue.
    Requires scaling workers to handle concurrency.
    """
    try:
        # Enqueue task (fire and forget for throughput benchmark)
        task = query_llm_task.delay(prompt, delay_ms)
        return {
            "status": "queued",
            "task_id": task.id,
            "message": "Query submitted to Celery queue"
        }
    except Exception as e:
        logger.error(f"Failed to enqueue task: {e}")
        return {"status": "error", "error": str(e)}


@app.post("/v1/agents/query/blocking")
async def submit_agent_query_blocking(
    prompt: str,
    delay_ms: int = 500
) -> Dict[str, Any]:
    """
    Submit and wait for agent query.
    This blocks the FastAPI worker thread while waiting for Celery.
    """
    try:
        start_time = time.time()
        task = query_llm_task.delay(prompt, delay_ms)
        
        # Wait for result (blocking the FastAPI thread)
        result = task.get(timeout=30)
        
        elapsed_ms = (time.time() - start_time) * 1000
        result["elapsed_ms"] = elapsed_ms
        result["worker_type"] = "celery"
        
        return result
    except Exception as e:
        logger.error(f"Query failed: {e}")
        return {"status": "error", "error": str(e)}


@app.get("/v1/health")
async def health_check() -> Dict[str, str]:
    return {"status": "healthy"}


@app.get("/v1/metrics/celery")
async def celery_metrics() -> Dict[str, Any]:
    """Return basic Celery metrics"""
    inspect = celery_app.control.inspect()
    stats = inspect.stats()
    active = inspect.active()
    
    return {
        "workers": stats or {},
        "active_tasks": active or {},
        "timestamp": datetime.utcnow().isoformat(),
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001, workers=4)
