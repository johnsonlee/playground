buildscript {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${embeddedKotlinVersion}")
    }
}

plugins {
    id("com.android.application") version libs.versions.android.gradle.plugin
    id("org.jetbrains.kotlin.android") version embeddedKotlinVersion
}

android {
    namespace = "io.johnsonlee.playground"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.johnsonlee.playground"
        minSdk = 24
        targetSdk = 32
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.androidx.lifecycle.common.java8)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
}
