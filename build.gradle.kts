import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.5.14"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.spring") version "1.9.23"
    kotlin("plugin.jpa") version "1.9.23"
    kotlin("plugin.allopen") version "1.9.23"
}

group = "com.github.senocak"
version = "0.0.1"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

springBoot {
    buildInfo {
        properties {
            this.name = "Spring Kotlin Oracle Hikari vs Ucp comparison"
            this.version = "0.0.1"
        }
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(dependencyNotation = "org.springframework.boot:spring-boot-starter-web")
    implementation(dependencyNotation = "org.springframework.boot:spring-boot-starter-data-jpa")
    implementation(dependencyNotation = "com.oracle.database.jdbc:ojdbc11:23.6.0.24.10") {
        description = "Oracle JDBC Driver"
    }
    implementation(dependencyNotation = "com.oracle.database.jdbc:ucp11:23.6.0.24.10"){
        description = "Oracle Universal Connection Pool"
    }
    implementation(dependencyNotation = "com.zaxxer:HikariCP") {
        description = "Hikari JDBC Connection Pool"
    }
    implementation(dependencyNotation = "com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation(dependencyNotation = "org.jetbrains.kotlin:kotlin-reflect")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    maxHeapSize = "1G"
}

allOpen {
    annotation(fqName = "javax.persistence.Entity")
    annotation(fqName = "javax.persistence.Embeddable")
    annotation(fqName = "javax.persistence.MappedSuperclass")
    annotation(fqName = "jakarta.persistence.Entity")
    annotation(fqName = "jakarta.persistence.Embeddable")
    annotation(fqName = "jakarta.persistence.MappedSuperclass")
}