package com.github.senocak

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

fun main(args: Array<String>) {
    runApplication<SpringKotlinApplication>(*args)
}

@RestController
@RequestMapping(value = ["/v1/pools"])
@ConfigurationPropertiesScan
@SpringBootApplication
class SpringKotlinApplication(
    private val comparisonService: ConnectionPoolComparisonService,
) {
    @GetMapping(value = ["/status"])
    fun status(): List<PoolSnapshot> = comparisonService.snapshots()

    @GetMapping(value = ["/compare"])
    fun compare(
        @RequestParam(name = "iterations", required = false, defaultValue = "100") iterations: Int,
        @RequestParam(name = "concurrency", required = false, defaultValue = "10") concurrency: Int,
        @RequestParam(name = "warmup", required = false, defaultValue = "10") warmupIterations: Int,
        @RequestParam(name = "query", required = false, defaultValue = "select 1 from dual") query: String,
    ): PoolComparisonResponse =
        comparisonService.compare(
            iterations = iterations,
            concurrency = concurrency,
            warmupIterations = warmupIterations,
            query = query,
        )
}
