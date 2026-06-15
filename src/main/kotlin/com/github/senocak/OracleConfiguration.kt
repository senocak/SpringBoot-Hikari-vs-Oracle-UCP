package com.github.senocak

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.time.Duration
import java.util.Locale
import javax.sql.DataSource
import oracle.ucp.jdbc.PoolDataSource
import oracle.ucp.jdbc.PoolDataSourceFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.env.Environment
import org.springframework.core.env.getProperty

@Configuration
class OracleConfiguration(
    private val env: Environment,
) {
    @Bean(name = ["ucpDataSource"])
    fun ucpDataSource(dsp: DataSourceProperties): PoolDataSource =
        PoolDataSourceFactory.getPoolDataSource().also { dataSource: PoolDataSource ->
            dataSource.url = dsp.url
            dataSource.user = dsp.username
            dataSource.password = dsp.password
            dataSource.connectionFactoryClassName = env.getProperty("spring.datasource.ucp.connection-factory-class-name", "oracle.jdbc.pool.OracleDataSource")
            dataSource.connectionPoolName = env.getProperty("spring.datasource.ucp.connection-pool-name", "SpringKotlinOracleUcpPool")
            dataSource.initialPoolSize = env.getProperty<Int>("spring.datasource.ucp.initial-pool-size", 15)
            dataSource.minPoolSize = env.getProperty<Int>("spring.datasource.ucp.min-pool-size", 10)
            dataSource.maxPoolSize = env.getProperty<Int>("spring.datasource.ucp.max-pool-size", 30)
            dataSource.timeoutCheckInterval = env.getProperty<Int>("spring.datasource.ucp.timeout-check-interval", 30)
            dataSource.inactiveConnectionTimeout = env.getProperty<Int>("spring.datasource.ucp.inactive-connection-timeout", 60)
            dataSource.connectionWaitDuration = Duration.ofSeconds(env.getProperty<Long>("spring.datasource.ucp.connection-wait-timeout", 30L))
            dataSource.sqlForValidateConnection = env.getProperty("spring.datasource.ucp.sql-for-validate-connection", "select * from dual")
            dataSource.validateConnectionOnBorrow = env.getProperty<Boolean>("spring.datasource.ucp.validate-connection-on-borrow", true)
            dataSource.secondsToTrustIdleConnection = env.getProperty<Int>("spring.datasource.ucp.seconds-to-trust-idle-connection", 1)
        }

    @Bean(name = ["hikariDataSource"], destroyMethod = "close")
    fun hikariDataSource(dsp: DataSourceProperties): HikariDataSource {
        val config: HikariConfig = HikariConfig().also { hikariConfig: HikariConfig ->
            hikariConfig.jdbcUrl = dsp.url
            hikariConfig.username = dsp.username
            hikariConfig.password = dsp.password
            hikariConfig.poolName = env.getProperty("spring.datasource.hikari.pool-name", "SpringKotlinOracleHikariPool")
            hikariConfig.minimumIdle = env.getProperty<Int>("spring.datasource.hikari.minimum-idle", 10)
            hikariConfig.maximumPoolSize = env.getProperty<Int>("spring.datasource.hikari.maximum-pool-size", 30)
            hikariConfig.connectionTimeout = env.getProperty<Long>("spring.datasource.hikari.connection-timeout", 30_000L)
            hikariConfig.idleTimeout = env.getProperty<Long>("spring.datasource.hikari.idle-timeout", 60_000L)
            hikariConfig.maxLifetime = env.getProperty<Long>("spring.datasource.hikari.max-lifetime", 1_800_000L)
            hikariConfig.validationTimeout = env.getProperty<Long>("spring.datasource.hikari.validation-timeout", 5_000L)
            hikariConfig.initializationFailTimeout = env.getProperty<Long>("spring.datasource.hikari.initialization-fail-timeout", -1L)

            val connectionTestQuery: String = env.getProperty("spring.datasource.hikari.connection-test-query", "")
            if (connectionTestQuery.isNotBlank()) {
                hikariConfig.connectionTestQuery = connectionTestQuery
            }
        }
        return HikariDataSource(config)
    }

    @Bean(name = ["dataSource"])
    @Primary
    fun dataSource(
        @Qualifier(value = "ucpDataSource") ucpDataSource: PoolDataSource,
        @Qualifier(value = "hikariDataSource") hikariDataSource: HikariDataSource,
    ): DataSource {
        val poolType: String = env.getProperty("spring.datasource.pool-type", "ucp").lowercase(locale = Locale.ROOT)
        return when (poolType) {
            "hikari" -> hikariDataSource
            "ucp" -> ucpDataSource
            else -> throw IllegalArgumentException("Unsupported spring.datasource.pool-type '$poolType'. Use 'ucp' or 'hikari'.")
        }
    }
}