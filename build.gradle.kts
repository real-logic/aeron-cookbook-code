import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    java
    checkstyle
    alias(libs.plugins.versions)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.AZUL)
    }
}

checkstyle {
    maxWarnings = 0
    toolVersion = libs.versions.checkstyleVersion.get()
}

version = "0.0.1-SNAPSHOT"

defaultTasks("check", "build", "test", "uberJar")

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

tasks.withType<DependencyUpdatesTask> {
    // Reject all non stable versions
    rejectVersionIf {
        isNonStable(candidate.version)
    }
}
