import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("jvm") version embeddedKotlinVersion
    kotlin("plugin.spring") version embeddedKotlinVersion
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "io.johnsonlee.playground"
version = "0.1.0"

dependencies {
    implementation(fileTree("libs"))
    implementation(project(":libs"))
    implementation(kotlin("bom"))
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation(libs.sandbox)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.module.jsonschema)
    implementation(libs.micrometer.registry.promehteus)
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.boot.starter.test)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2023.0.2")
    }
}

subprojects {
    group = rootProject.group
    version = rootProject.version
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val jar by tasks.getting(Jar::class) {
    enabled = false
}

val bootJar by tasks.getting(BootJar::class) {
    enabled = true
    archiveFileName.set("app.jar")
    mainClass.set("io.johnsonlee.springboot.starter.StarterApplicationKt")
}
