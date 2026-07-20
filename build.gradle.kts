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

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("legacyminingworld.version", project.version.toString())
}

tasks.register<Test>("geologyEngineTest") {
    description = "Runs the Phase 2A pure geology engine review suite."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        excludeTags("geology-adapter", "ore-adapter")
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

tasks.jar {
    archiveBaseName = "LegacyMiningWorld"
    archiveVersion = project.version.toString()
}
