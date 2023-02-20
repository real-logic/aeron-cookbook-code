plugins {
    java
    checkstyle
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
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
