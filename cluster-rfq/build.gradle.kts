
plugins {
    application
    checkstyle
    id ("me.champeau.jmh") version "0.6.8"
}

val generatedEiderDir = file("src/main/generated")
val generatedSbeDir = file("${buildDir}/generated/src/main/java")
val codecGeneration = configurations.create("codecGeneration")

jmh {
    warmupIterations.set(5)
    iterations.set(5)
    fork.set(1)
}

dependencies {
    jmh(libs.jmhcore)
    jmh(libs.jmhannprocess)
    "codecGeneration"(libs.sbe)
    checkstyle(libs.checkstyle)
    implementation(libs.aeron)
    implementation(libs.agrona)
    implementation(libs.slf4j)
    implementation(libs.logback)
    testImplementation(libs.aeron)
    testImplementation(libs.agrona)
}


sourceSets {
    main {
        java.srcDirs("src/main/java", generatedEiderDir, generatedSbeDir)
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


tasks {

    task("generateCodecs", JavaExec::class) {
        group = "sbe"
        val codecsFile = "src/main/resources/messages.xml"
        val sbeFile = "src/main/resources/sbe/sbe.xsd"
        inputs.files(codecsFile, sbeFile)
        outputs.dir(generatedSbeDir)
        classpath = codecGeneration
        mainClass.set("uk.co.real_logic.sbe.SbeTool")
        args = listOf(codecsFile)
        systemProperties["sbe.output.dir"] = generatedSbeDir
        systemProperties["sbe.target.language"] = "Java"
        systemProperties["sbe.validation.xsd"] = sbeFile
        systemProperties["sbe.validation.stop.on.error"] = "true"
        outputs.dir(generatedSbeDir)
    }

    compileJava {
        dependsOn("generateCodecs")
    }

}
