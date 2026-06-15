package com.github.senocak

data class PoolComparisonResponse(
    val timestamp: String,
    val settings: PoolComparisonSettings,
    val results: List<PoolBenchmarkResult>,
)

data class PoolComparisonSettings(
    val query: String,
    val iterations: Int,
    val concurrency: Int,
    val warmupIterations: Int,
)

data class PoolBenchmarkResult(
    val pool: String,
    val poolName: String,
    val requestedOperations: Int,
    val successfulOperations: Int,
    val failedOperations: Int,
    val totalElapsedMs: Double,
    val throughputOpsPerSecond: Double,
    val latencyMs: LatencyStats,
    val before: PoolSnapshot,
    val after: PoolSnapshot,
    val firstError: String?,
)

data class LatencyStats(
    val min: Double = 0.0,
    val average: Double = 0.0,
    val p50: Double = 0.0,
    val p95: Double = 0.0,
    val p99: Double = 0.0,
    val max: Double = 0.0,
)

data class PoolSnapshot(
    val pool: String,
    val poolName: String,
    val configuration: PoolConfigurationSnapshot,
    val live: PoolLiveSnapshot,
)

data class PoolConfigurationSnapshot(
    val initialPoolSize: Int?,
    val minimumIdle: Int?,
    val maximumPoolSize: Int?,
    val connectionTimeoutMs: Long?,
    val idleTimeoutMs: Long?,
    val maxLifetimeMs: Long?,
    val validationQuery: String?,
)

data class PoolLiveSnapshot(
    val activeConnections: Int?,
    val idleConnections: Int?,
    val totalConnections: Int?,
    val threadsAwaitingConnection: Int?,
)
