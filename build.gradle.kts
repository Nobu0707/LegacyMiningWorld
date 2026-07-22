import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    java
}

group = "net.nobu0707"
version = providers.gradleProperty("legacyminingworld_version").get()

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.69-stable")

    testImplementation("io.papermc.paper:paper-api:26.1.2.build.69-stable")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val multiverseVerifier by sourceSets.creating {
    resources {
        srcDir("src/test/resources")
        include(
            "plugin.yml",
            "geology-smoke-anchors.tsv",
            "ore-smoke-anchors.tsv",
            "large-scale-grid.properties"
        )
    }
}

val multiverseVerifierTest by sourceSets.creating {
    compileClasspath += multiverseVerifier.output
    runtimeClasspath += multiverseVerifier.output
}

dependencies {
    add(multiverseVerifier.compileOnlyConfigurationName,
        "io.papermc.paper:paper-api:26.1.2.build.69-stable")
    add(multiverseVerifierTest.implementationConfigurationName,
        "io.papermc.paper:paper-api:26.1.2.build.69-stable")
    add(multiverseVerifierTest.implementationConfigurationName,
        "org.junit.jupiter:junit-jupiter:5.13.4")
    add(multiverseVerifierTest.runtimeOnlyConfigurationName,
        "org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

tasks.named<ProcessResources>(multiverseVerifier.processResourcesTaskName) {
    filteringCharset = "UTF-8"
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("large-scale-model")
    }
    systemProperty("legacyminingworld.version", project.version.toString())
}

tasks.register<Test>("geologyEngineTest") {
    description = "Runs the Phase 2A pure geology engine review suite."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        excludeTags("geology-adapter", "ore-adapter", "large-scale-model")
    }
    filter {
        includeTestsMatching("net.nobu0707.legacyminingworld.geology.*")
    }
    systemProperty("legacyminingworld.version", project.version.toString())
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.register<Test>("geologyAdapterTest") {
    description = "Runs the Phase 2B Paper adapter and in-memory world review suite."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("geology-adapter")
    }
    systemProperty("legacyminingworld.version", project.version.toString())
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.register<Test>("oreEngineTest") {
    description = "Runs the Phase 3A pure deterministic ore engine review suite."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        excludeTags("ore-adapter")
    }
    filter {
        includeTestsMatching("net.nobu0707.legacyminingworld.ore.*")
    }
    systemProperty("legacyminingworld.version", project.version.toString())
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.register<Test>("oreAdapterTest") {
    description = "Runs the Phase 3B Paper ore adapter and combined underground review suite."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("ore-adapter")
    }
    systemProperty("legacyminingworld.version", project.version.toString())
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.register<Test>("multiverseVerifierTest") {
    description = "Runs the Phase 4A test-only Multiverse verifier suite."
    group = "verification"
    testClassesDirs = multiverseVerifierTest.output.classesDirs
    classpath = multiverseVerifierTest.runtimeClasspath
    useJUnitPlatform {
        excludeTags("large-scale-verifier")
    }
    filter {
        isFailOnNoMatchingTests = true
    }
    systemProperty("legacyminingworld.version", project.version.toString())
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.register<Test>("largeScaleVerifierTest") {
    description = "Runs the Phase 4B1 large-scale verifier parser and reporting suite."
    group = "verification"
    testClassesDirs = multiverseVerifierTest.output.classesDirs
    classpath = multiverseVerifierTest.runtimeClasspath
    useJUnitPlatform {
        includeTags("large-scale-verifier")
    }
    filter {
        isFailOnNoMatchingTests = true
    }
    systemProperty("legacyminingworld.version", project.version.toString())
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.register<Test>("largeScaleModelTest") {
    description = "Builds the deterministic Phase 4B1 1,089-chunk pure-model reports."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("large-scale-model")
    }
    systemProperty("legacyminingworld.version", project.version.toString())
    systemProperty(
        "legacyminingworld.largeScaleReportDir",
        layout.buildDirectory.dir("large-scale-model/reports").get().asFile.absolutePath
    )
    outputs.upToDateWhen { false }
    doFirst {
        delete(layout.buildDirectory.dir("large-scale-model/reports"))
    }
    filter {
        isFailOnNoMatchingTests = true
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.register<Jar>("multiverseVerifierJar") {
    description = "Builds the test-only Phase 4A/4B1/4B2 Multiverse integration verifier."
    group = "verification"
    dependsOn(multiverseVerifier.classesTaskName)
    archiveBaseName = "LegacyMiningWorld-MultiverseVerifier"
    archiveVersion = project.version.toString()
    from(multiverseVerifier.output)
    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
}

tasks.jar {
    archiveBaseName = "LegacyMiningWorld"
    archiveVersion = project.version.toString()
    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
}
