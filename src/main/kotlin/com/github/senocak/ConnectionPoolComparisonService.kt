package com.github.senocak

import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.HikariPoolMXBean
import java.sql.Connection
import java.sql.PreparedStatement
import oracle.ucp.jdbc.PoolDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.sql.ResultSet
import java.time.Instant
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource
import kotlin.math.ceil

@Service
class ConnectionPoolComparisonService(
    @Qualifier(value = "ucpDataSource") private val ucpDataSource: PoolDataSource,
    @Qualifier(value = "hikariDataSource") private val hikariDataSource: HikariDataSource
) {
    fun snapshots(): List<PoolSnapshot> =
        listOf(
            snapshotUcp(),
            snapshotHikari(),
        )

    fun compare(iterations: Int, concurrency: Int, warmupIterations: Int, query: String): PoolComparisonResponse {
        val settings = PoolComparisonSettings(
            query = query,
            iterations = iterations,
            concurrency = concurrency,
            warmupIterations = warmupIterations,
        )
        return PoolComparisonResponse(
            timestamp = Instant.now().toString(),
            settings = settings,
            results = listOf(
                benchmark(pool = "ucp", dataSource = ucpDataSource, settings = settings, snapshot = ::snapshotUcp),
                benchmark(pool = "hikari", dataSource = hikariDataSource, settings = settings, snapshot = ::snapshotHikari),
            ),
        )
    }

    private fun benchmark(pool: String, dataSource: DataSource, settings: PoolComparisonSettings,
                          snapshot: () -> PoolSnapshot): PoolBenchmarkResult {
        val before: PoolSnapshot = snapshot()
        repeat(times = settings.warmupIterations) { _: Int ->
            executeQuery(dataSource = dataSource, query = settings.query)
        }
        val latencies: MutableList<Long> = Collections.synchronizedList(mutableListOf<Long>())
        val operationIndex = AtomicInteger(0)
        val failures = AtomicInteger(0)
        val firstError = AtomicReference<String?>()
        val startLatch = CountDownLatch(1)
        val executor: ExecutorService = Executors.newFixedThreadPool(settings.concurrency)
        val futures: List<Future<*>> = (0 until settings.concurrency).map { _: Int ->
            executor.submit {
                startLatch.await()
                while (true) {
                    val currentOperation: Int = operationIndex.getAndIncrement()
                    if (currentOperation >= settings.iterations) {
                        return@submit
                    }
                    val startedAt: Long = System.nanoTime()
                    try {
                        executeQuery(dataSource = dataSource, query = settings.query)
                        latencies.add(element = System.nanoTime() - startedAt)
                    } catch (ex: Exception) {
                        failures.incrementAndGet()
                        firstError.compareAndSet(null, "${ex.javaClass.simpleName}: ${ex.message}")
                    }
                }
            }
        }
        val startedAt: Long = System.nanoTime()
        startLatch.countDown()
        futures.forEach { future: Future<*> -> future.get() }
        val totalElapsedNanos: Long = System.nanoTime() - startedAt
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)

        val successfulOperations: Int = latencies.size
        val totalElapsedSeconds: Double = totalElapsedNanos / 1_000_000_000.0
        return PoolBenchmarkResult(
            pool = pool,
            poolName = before.poolName,
            requestedOperations = settings.iterations,
            successfulOperations = successfulOperations,
            failedOperations = failures.get(),
            totalElapsedMs = nanosToMillis(nanos = totalElapsedNanos),
            throughputOpsPerSecond = if (totalElapsedSeconds > 0) successfulOperations / totalElapsedSeconds else 0.0,
            latencyMs = latencyStats(latenciesNanos = latencies.toList()),
            before = before,
            after = snapshot(),
            firstError = firstError.get(),
        )
    }

    private fun executeQuery(dataSource: DataSource, query: String) {
        dataSource.connection.use { connection: Connection ->
            connection.prepareStatement(query).use { statement: PreparedStatement ->
                val hasResultSet: Boolean = statement.execute()
                if (hasResultSet) {
                    statement.resultSet.use { resultSet: ResultSet ->
                        while (resultSet.next()) {
                            // Drain the result set so every pool executes identical JDBC work.
                        }
                    }
                }
            }
        }
    }

    private fun snapshotUcp(): PoolSnapshot {
        val activeConnections: Int? = readInt { ucpDataSource.borrowedConnectionsCount }
        val idleConnections: Int? = readInt { ucpDataSource.availableConnectionsCount }
        return PoolSnapshot(
            pool = "ucp",
            poolName = ucpDataSource.connectionPoolName,
            configuration = PoolConfigurationSnapshot(
                initialPoolSize = ucpDataSource.initialPoolSize,
                minimumIdle = ucpDataSource.minPoolSize,
                maximumPoolSize = ucpDataSource.maxPoolSize,
                connectionTimeoutMs = ucpDataSource.connectionWaitDuration.toMillis(),
                idleTimeoutMs = ucpDataSource.inactiveConnectionTimeout * 1_000L,
                maxLifetimeMs = null,
                validationQuery = ucpDataSource.sqlForValidateConnection,
            ),
            live = PoolLiveSnapshot(
                activeConnections = activeConnections,
                idleConnections = idleConnections,
                totalConnections = total(activeConnections = activeConnections, idleConnections = idleConnections),
                threadsAwaitingConnection = null,
            ),
        )
    }

    private fun snapshotHikari(): PoolSnapshot {
        val poolMxBean: HikariPoolMXBean? = hikariDataSource.hikariPoolMXBean
        return PoolSnapshot(
            pool = "hikari",
            poolName = hikariDataSource.poolName,
            configuration = PoolConfigurationSnapshot(
                initialPoolSize = null,
                minimumIdle = hikariDataSource.minimumIdle,
                maximumPoolSize = hikariDataSource.maximumPoolSize,
                connectionTimeoutMs = hikariDataSource.connectionTimeout,
                idleTimeoutMs = hikariDataSource.idleTimeout,
                maxLifetimeMs = hikariDataSource.maxLifetime,
                validationQuery = hikariDataSource.connectionTestQuery,
            ),
            live = PoolLiveSnapshot(
                activeConnections = poolMxBean?.activeConnections,
                idleConnections = poolMxBean?.idleConnections,
                totalConnections = poolMxBean?.totalConnections,
                threadsAwaitingConnection = poolMxBean?.threadsAwaitingConnection,
            ),
        )
    }

    private fun readInt(read: () -> Int): Int? =
        try {
            read()
        } catch (ex: Exception) {
            null
        }

    private fun total(activeConnections: Int?, idleConnections: Int?): Int? =
        if (activeConnections != null && idleConnections != null) {
            activeConnections + idleConnections
        } else {
            null
        }

    private fun latencyStats(latenciesNanos: List<Long>): LatencyStats {
        if (latenciesNanos.isEmpty()) {
            return LatencyStats()
        }
        val sorted: List<Long> = latenciesNanos.sorted()
        return LatencyStats(
            min = nanosToMillis(nanos = sorted.first()),
            average = nanosToMillis(nanos = sorted.average()),
            p50 = percentile(sorted = sorted, percentile = 0.50),
            p95 = percentile(sorted = sorted, percentile = 0.95),
            p99 = percentile(sorted = sorted, percentile = 0.99),
            max = nanosToMillis(nanos = sorted.last()),
        )
    }

    private fun percentile(sorted: List<Long>, percentile: Double): Double {
        val index: Int = (ceil(x = percentile * sorted.size).toInt() - 1).coerceIn(minimumValue = 0, maximumValue = sorted.lastIndex)
        return nanosToMillis(nanos = sorted[index])
    }

    private fun nanosToMillis(nanos: Long): Double = nanos / 1_000_000.0
    private fun nanosToMillis(nanos: Double): Double = nanos / 1_000_000.0
}
