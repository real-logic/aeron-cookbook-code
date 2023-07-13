
plugins {
    application
    checkstyle
}

val generatedDir = file("${buildDir}/generated/src/main/java")
val codecGeneration = configurations.create("codecGeneration")

dependencies {
    "codecGeneration"(libs.sbe)
    checkstyle(libs.checkstyle)
    implementation(libs.agrona)
    implementation(libs.aeron)
    implementation(libs.slf4j)
    implementation(libs.logback)
    testImplementation(libs.bundles.testing)
}

sourceSets {
    main {
        java.srcDirs("src/main/java", generatedDir)
    }
}

tasks {

    task("generateCodecs", JavaExec::class) {
        group = "sbe"
        val codecsFile = "src/main/resources/messages.xml"
        val sbeFile = "src/main/resources/sbe/sbe.xsd"
        inputs.files(codecsFile, sbeFile)
        outputs.dir(generatedDir)
        classpath = codecGeneration
        mainClass.set("uk.co.real_logic.sbe.SbeTool")
        args = listOf(codecsFile)
        systemProperties["sbe.output.dir"] = generatedDir
        systemProperties["sbe.validation.xsd"] = sbeFile
        systemProperties["sbe.target.language"] = "Java"
        systemProperties["sbe.validation.stop.on.error"] = "true"
        outputs.dir(generatedDir)
    }

    compileJava {
        dependsOn("generateCodecs")
    }
}

testing {
    suites {
        // Configure the built-in test suite
        @Suppress("UNUSED_VARIABLE")
        val test by getting(JvmTestSuite::class) {
            // Use JUnit Jupiter test framework
            useJUnitJupiter(libs.versions.junitVersion.get())
        }
    }
}
