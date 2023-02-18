
plugins {
    application
    checkstyle
}

dependencies {
    checkstyle(libs.checkstyle)
    implementation(libs.aeron)
    implementation(libs.agrona)
    implementation(libs.slf4j)
    implementation(libs.logback)
    testImplementation(libs.aeron)
    testImplementation(libs.agrona)
}

val generatedDir = file("src/main/generated")

sourceSets {
    main {
        java.srcDirs("src/main/java", generatedDir)
    }
}

tasks {
    test {
        jvmArgs("--add-opens=java.base/sun.nio.ch=ALL-UNNAMED")
    }
}


testing {
    suites {
        // Configure the built-in test suite
        val test by getting(JvmTestSuite::class) {
            // Use JUnit Jupiter test framework
            useJUnitJupiter(libs.versions.junitVersion.get())
        }
    }
}
