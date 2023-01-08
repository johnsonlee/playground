import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("jvm") version embeddedKotlinVersion
    kotlin("plugin.spring") version embeddedKotlinVersion
    id("org.springframework.boot") version "2.7.14"
    id("io.spring.dependency-management") version "1.1.2"
}

group = "io.johnsonlee.playground"
version = "0.1.0"

dependencies {
    implementation(fileTree("libs"))
    implementation(project(":libs"))
    implementation(kotlin("bom"))
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation(libs.android.tools.build.aapt2.proto)
    implementation(libs.android.tools.external.intellij.core)
    implementation(libs.android.tools.layoutlib.api)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.module.jsonschema)
    implementation(libs.kxml2)
    implementation(libs.layoutlib.native.jdk11)
    implementation(libs.okio)
    implementation(libs.protobuf)
    implementation(libs.android.tools.common)
    implementation(libs.android.tools.sdk.common)
    implementation(libs.androidx.arch.core.common)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.constraintlayout.resolver)
    implementation(libs.androidx.lifecycle.common.java8)
    implementation(libs.micrometer.registry.promehteus)
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.boot.starter.test)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2021.0.8")
    }
}

subprojects {
    group = rootProject.group
    version = rootProject.version
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

val jar by tasks.getting(Jar::class) {
    enabled = false
}

val bootJar by tasks.getting(BootJar::class) {
    enabled = true
    archiveFileName.set("app.jar")
    mainClass.set("io.johnsonlee.springboot.starter.StarterApplicationKt")
}
