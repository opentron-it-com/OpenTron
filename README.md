<p align="center">
  <img
    src="assets/OpenTron_Horizontal_Logo.png"
    alt="JVMAI - Stateful Multi-Agent AI Orchestrator & Confidential AI Engine for Java"
    width="450">
</p>

<h1 align="center">JVMAI <sub style="font-size: 30%;">formerly Open Tron</sub></h1>
<p align="center">
  <strong>Personal AI on Personal Devices | Enterprise AI Agents at JVM Scale. Zero Python Bloat & Confidential TEE Runtime</strong>
</p>

<h3 align="center">
  How We Built a High-Density, Zero-Trust AI Agent Engine on a $1,000 Budget While Silicon Valley Burns Millions on Cloud Infrastructure.
</h3>

<p align="center">
  <a href="https://opentron.it.com">Website</a> •
  <a href="https://github.com/opentron-it-com/OpenTron/blob/main/benchmarks/OpenTron_Benchmark_Executive_Summary.pdf">Benchmarks (PDF)</a> •
  <a href="https://www.producthunt.com/products/opentron">Product Hunt</a> •
  <a href="#enterprise-confidential-ai-cjae">Confidential AI Spec</a> • 
</p>

---

## 🚀 What is JVMAI?

**JVMAI** is a high-density, stateful multi-agent AI orchestrator and secure runtime built natively on **Java 21**, **Spring Boot**, and **PostgreSQL**. 

While mainstream AI frameworks rely on heavy, resource-leaking Python runtimes (LangChain, CrewAI, AutoGen) that quickly incur massive RAM usage and complex infrastructure sprawl, JVMAI delivers enterprise-grade concurrency, zero-waste execution, and hardware-enforced privacy at native JVM speeds.

---

## 📉 The Core Flaw of the Mainstream AI Hype Stack

Modern AI frameworks predominantly rely on Python stacks (e.g., FastAPI, LangChain, Celery). As multi-agent systems scale in production, critical bottlenecks destroy profit margins:

* **Process Multiplication:** Scaling concurrency forces new worker processes to fork, duplicating runtimes and devouring system RAM.
* **Infrastructure Sprawl:** Managing background agent tasks requires external Redis brokers, Celery instances, and complex message queues.
* **Operational Overhead:** More infrastructure services directly translate to bloated memory footprints, higher CPU utilization, and runaway cloud compute expenses.

### The JVMAI Solution
By leveraging **Java 21 Virtual Threads (Project Loom)**, **Spring Boot**, and strongly typed contracts, JVMAI executes agent orchestration natively inside a single JVM container, completely eliminating unnecessary microservice overhead.

---
<a id="enterprise-confidential-ai-cjae"></a>
## 🛡️ Enterprise Confidential AI (CJAE Architecture)

For enterprise workloads demanding zero-trust security, JVMAI integrates the **Confidential Java AI Engine (CJAE)** specification. This turns the JVM into a secure, hardware-isolated fortress for sensitive data, LLM weights, and vector caches.

* **Hardware-Enforced TEE Isolation:** Runs natively inside **Google Cloud Confidential Space** and **AWS Nitro Enclaves**, isolating data-in-use from cloud providers, host operators, and guest OS vulnerabilities.
* **Project Panama Zero-Copy Safety:** Bypasses legacy JNI latency by utilizing the Foreign Function & Memory (FFM) API. Sensitive data structures are restricted exclusively to off-heap `MemorySegment` blocks with automated hardware-accelerated memory zeroing (`internalSegment.fill((byte) 0)`) upon task completion.
* **GraalVM Native Image AOT Compilation:** Strips away standard dynamic JVM bloat into a secure, ultra-lean standalone binary optimized for maximum execution speed (`-O3`) and strict zero-dependency footprints.
* **Declarative Security:** Protects methods and workflows instantly using the `@ConfidentialAI` aspect proxy combined with cryptographically verified OIDC/JWT multi-tenant context validation.

---

## 💰 Economic Reality: Hardware Saturation vs. Cloud Sprawl

| Metric | Python Frameworks *(LangChain / CrewAI / FastAPI)* | JVMAI *(Java 21 / Spring Boot / Postgres / TEE)* |
| :--- | :--- | :--- |
| **Memory Utilization** | **High Overhead:** Forked worker processes duplicate runtimes and rapidly trigger out-of-memory errors. | **High Density:** 10,000+ concurrent tasks share a single, lean JVM footprint with minimal memory growth. |
| **API & I/O Latency Handling** | **Idle Resource Usage:** Synchronous/blocking calls lock OS threads during LLM token streaming. | **Zero-Waste Execution:** Java 21 Virtual Threads unmount during network I/O, saturating hardware capacity. |
| **Task Queueing & Backgrounding** | **External Dependencies:** Requires separate infrastructure (Redis, Celery, RabbitMQ) to manage jobs. | **In-Process Consolidation:** Full agent workflow orchestration and job queues run natively in one process. |
| **Security & Privacy** | **Vulnerable Runtimes:** Open to host-level inspection and memory snooping in standard cloud nodes. | **Hardware-Enforced TEE:** Encrypted memory via AMD SEV-SNP, Intel TDX, and AWS Nitro enclaves. |
| **Type Safety & Reliability** | **Runtime Type Errors:** Dynamic typing issues surface mid-execution during costly LLM calls. | **Compile-Time Safety:** Strongly typed Java contracts catch payload bugs before invoking paid LLM APIs. |

---

## 📊 Benchmarks & Proof of Performance

Tested under a simulated 500 ms LLM API latency profile:
* **Peak Throughput:** ~2,750 tasks/sec within a single JVM process.
* **Median Latency (p50):** 178.4 ms at 1,000 concurrent tasks.
* **Reliability:** 100% completion across 9,100 test tasks without connection timeouts.

👉 [Read the Complete JVMAI Benchmark Report (PDF)](https://github.com/open-tron-ai/OpenTron/blob/main/benchmarks/OpenTron_Benchmark_Executive_Summary.pdf)

---

## 🚀 Quick Links & Documentation

* **[Executive Summary](EXECUTIVE_SUMMARY.md)**
* **[Developer Architecture Diagram](DEVELOPER_ARCHITECTURE.md)**
* **[REST API Reference](API_REFERENCE.md)**

---

## 📄 License & Commercial Terms

This repository is source-available. The code is publicly visible for evaluation and review purposes only. No rights are granted to use, modify, distribute, or commercialize this software without explicit written permission from the author.

© 2026 Ermis & Roxana. All Rights Reserved.
