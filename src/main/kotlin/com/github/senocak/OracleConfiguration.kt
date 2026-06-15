package com.github.senocak

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.Locale
import javax.sql.DataSource
import oracle.ucp.jdbc.PoolDataSource
import oracle.ucp.jdbc.PoolDataSourceFactory
import oracle.ucp.jdbc.PoolDataSourceImpl
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.env.Environment

@Configuration
class OracleConfiguration1(
    private val env: Environment,
) {
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    class DataSourceConfigs: DataSourceProperties() {
        lateinit var ddl: String
        lateinit var oracleucp: PoolDataSourceImpl
        lateinit var hikari: HikariConfig
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    fun hikariConfig(): HikariConfig {
        return HikariConfig()
    }

    @Bean(name = ["ucpDataSource"])
    fun ucpDataSource(dataSourceProperties: DataSourceConfigs): PoolDataSource =
        PoolDataSourceFactory.getPoolDataSource()
            .also { dataSource: PoolDataSource ->
                dataSource.url = dataSourceProperties.url
                dataSource.user = dataSourceProperties.username
                dataSource.password = dataSourceProperties.password
                // UCP-specific configurations
                dataSource.connectionFactoryClassName = dataSourceProperties.oracleucp.connectionFactoryClassName
                dataSource.initialPoolSize = dataSourceProperties.oracleucp.initialPoolSize
                dataSource.minPoolSize = dataSourceProperties.oracleucp.minPoolSize
                dataSource.maxPoolSize = dataSourceProperties.oracleucp.maxPoolSize
                dataSource.timeoutCheckInterval = dataSourceProperties.oracleucp.timeoutCheckInterval
                dataSource.inactiveConnectionTimeout = dataSourceProperties.oracleucp.inactiveConnectionTimeout
                dataSource.sqlForValidateConnection = dataSourceProperties.oracleucp.sqlForValidateConnection
                dataSource.validateConnectionOnBorrow = dataSourceProperties.oracleucp.validateConnectionOnBorrow
                dataSource.secondsToTrustIdleConnection = dataSourceProperties.oracleucp.secondsToTrustIdleConnection
            }

    @Bean(name = ["hikariDataSource"], destroyMethod = "close")
    fun hikariDataSource(dataSourceProperties: DataSourceConfigs): HikariDataSource =
        HikariDataSource(dataSourceProperties.hikari.also { dataSource: HikariConfig ->
            dataSource.jdbcUrl = dataSourceProperties.url
            dataSource.username = dataSourceProperties.username
            dataSource.password = dataSourceProperties.password
            dataSource.password = dataSourceProperties.password
        })


    @Bean(name = ["dataSource"])
    @Primary
    fun dataSource(hds: HikariDataSource, pds: PoolDataSource): DataSource {
        val poolType: String = env.getProperty("spring.datasource.pool-type", "ucp").lowercase(locale = Locale.ROOT)
        return when (poolType) {
            "hikari" -> hds
            "ucp" -> pds
            else -> throw IllegalArgumentException("Unsupported spring.datasource.pool-type '$poolType'. Use 'ucp' or 'hikari'.")
        }
    }
}
