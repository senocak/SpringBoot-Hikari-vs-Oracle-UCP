# Spring Kotlin Oracle Connection Pool Comparison

## Overview
This project is a **Spring + Kotlin + Oracle** sample application for comparing **Oracle Universal Connection Pool (UCP)** and **HikariCP** against the same Oracle database.

It includes:
- one named UCP datasource
- one named Hikari datasource
- a configurable primary datasource for JPA (`ucp` by default)
- REST endpoints that report pool size, live pool state, latency percentiles, throughput, and failures for both pools
- the existing custom Hibernate identifier generator backed by Oracle sequences

The ID generator part demonstrates how to:
- use a reusable custom `IdentifierGenerator`
- determine Oracle sequence names dynamically from entity metadata
- fetch IDs either **one by one** or **in batches**
- keep a small in-memory pool of prefetched IDs to reduce database round trips during bulk inserts
- support JDBC batching efficiently

The project compares two different approaches side-by-side:

1. **Dynamic custom sequence generation** (`User`)
2. **Standard JPA `@SequenceGenerator`** (`Role`)

This makes the project useful as a reference for teams that want flexible Oracle ID generation without hardcoding a sequence generator on every entity.

---

## Connection Pool Comparison

The application creates both pools at startup:

| Pool | Bean name | Default size |
|---|---|---|
| Oracle UCP | `ucpDataSource` | initial `15`, min `10`, max `30` |
| HikariCP | `hikariDataSource` | min idle `10`, max `30` |

The primary JPA datasource is selected with:

```yaml
spring:
  datasource:
    pool-type: ucp # ucp or hikari
```

or by environment variable:

```shell
DATASOURCE_POOL_TYPE=hikari
```

### Endpoints

```http
GET /v1/pools/status
```

Returns configured and live pool state for UCP and Hikari.

```http
GET /v1/pools/compare?iterations=100&concurrency=10&warmup=10
```

Runs the same `SELECT` query through both pools and returns:
- requested, successful, and failed operation counts
- total elapsed time
- throughput in operations per second
- min, average, p50, p95, p99, and max latency in milliseconds
- before/after pool metrics such as active, idle, total, and waiting connections where supported

The default benchmark query is:

```sql
select 1 from dual
```

Only `SELECT` statements are accepted by the comparison endpoint.

### Benchmark Configuration

```yaml
spring:
  datasource:
    comparison:
      query: select 1 from dual
      iterations: 100
      concurrency: 10
      warmup-iterations: 10
```

Pool sizes can be tuned independently:

```yaml
spring:
  datasource:
    ucp:
      initial-pool-size: 15
      min-pool-size: 10
      max-pool-size: 30
      connection-wait-timeout: 30
    hikari:
      minimum-idle: 10
      maximum-pool-size: 30
      connection-timeout: 30000
```