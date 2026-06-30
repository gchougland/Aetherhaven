plugins {
    `maven-publish`
    id("hytale-mod") version "0.+"
}

group = "com.hexvane"
version = "2.0.3"
val javaVersion = 25

repositories {
    mavenCentral()
    maven("https://maven.hytale-modding.info/releases") {
        name = "HytaleModdingReleases"
    }
    maven("https://cursemaven.com") {
        name = "CurseMaven"
    }
}

/** Jars merged into the published mod only (not the full dev {@code runtimeClasspath}). */
val modEmbed: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    // Core parser only. flexmark-all also embeds PDF/HTML converters (iText, OpenHTML) with tens of
    // thousands of extra classes that trigger CurseForge manual security review on upload.
    val flexmark = "com.vladsch.flexmark:flexmark:0.64.8"
    val gson = "com.google.code.gson:gson:2.11.0"
    implementation(flexmark)
    implementation(gson)
    modEmbed(flexmark)
    modEmbed(gson)
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.jspecify)
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

/**
 * Hytale loads each plugin from an isolated classloader with only the plugin jar (no Gradle lib folder).
 * Embed flexmark core + Gson via [modEmbed]. Do not use [configurations.runtimeClasspath] here: hytale-mod
 * adds HytaleServer.jar (~120MB) to runtimeClasspath for runServer, which must not ship in the release jar.
 */
tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        modEmbed
            .filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
            .map { zipTree(it) }
    }) {
        exclude("META-INF/INDEX.LIST")
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
    }
    // Mod Common/ + Server/ + manifest.json must win over anything merged from dependency jars.
    from(sourceSets.main.get().output.resourcesDir)
}

tasks.register("verifyReleaseJar") {
    group = "verification"
    description = "Fails if the release jar accidentally bundles HytaleServer or other blocked packages."
    dependsOn(tasks.jar)
    val releaseJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    inputs.file(releaseJar)
    doLast {
        val jarFile = releaseJar.get().asFile
        if (!jarFile.isFile) {
            error("Missing release jar: ${jarFile.absolutePath}")
        }
        val blocked =
            listOf(
                "com/hypixel/hytale/Main.class",
                "org/bouncycastle/",
                "native/win-x64/quiche.dll",
            )
        val jarExe =
            File(System.getProperty("java.home"), "bin/jar.exe").takeIf { it.isFile }
                ?: File(System.getProperty("java.home"), "bin/jar").takeIf { it.isFile }
        if (jarExe == null) {
            logger.lifecycle("verifyReleaseJar: jar tool not found; skipped content checks")
            return@doLast
        }
        val listing =
            ProcessBuilder(jarExe.absolutePath, "tf", jarFile.absolutePath)
                .redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                .readText()
        for (pattern in blocked) {
            if (listing.contains(pattern)) {
                error(
                    "Release jar ${jarFile.name} contains $pattern — do not embed HytaleServer on modEmbed/runtimeClasspath merge"
                )
            }
        }
        val sizeMb = jarFile.length() / (1024.0 * 1024.0)
        logger.lifecycle("verifyReleaseJar: ${jarFile.name} OK (${"%.1f".format(sizeMb)} MB, no HytaleServer)")
    }
}

hytale {
    // uncomment if you want to add the Assets.zip file to your external libraries;
    // ⚠️ CAUTION, this file is very big and might make your IDE unresponsive for some time!
    //
    // addAssetsDependency = true

    // uncomment if you want to develop your mod against the pre-release version of the game.
    //
    //updateChannel = "pre-release"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }

    withSourcesJar()
}

/**
 * hstats.dev mod UUID, resolved when Gradle runs (baked into [HstatsBuildMetadata]):
 * 1. environment variable {@code AETHERHAVEN_HSTATS_MOD_UUID}
 * 2. else Gradle property {@code hstats_mod_uuid} ({@code gradle.properties} or {@code -Phstats_mod_uuid=...})
 */
val hstatsModUuid: Provider<String> =
    providers
        .environmentVariable("AETHERHAVEN_HSTATS_MOD_UUID")
        .map { it.trim() }
        .filter(String::isNotEmpty)
        .orElse(providers.gradleProperty("hstats_mod_uuid").map { it.trim() }.orElse(""))

val generateHstatsBuildMetadata =
    tasks.register("generateHstatsBuildMetadata") {
        val outDir = layout.buildDirectory.dir("generated/sources/hstats/java")
        inputs.property("hstatsModUuid", hstatsModUuid)
        outputs.dir(outDir)

        doLast {
            val uuid = hstatsModUuid.get()
            val escaped =
                uuid
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
            val pkgDir = outDir.get().asFile.resolve("com/hexvane/aetherhaven/generated")
            pkgDir.mkdirs()
            pkgDir.resolve("HstatsBuildMetadata.java").writeText(
                """
                package com.hexvane.aetherhaven.generated;

                /** Compile-time HStats (hstats.dev) values. Generated by Gradle — do not edit. */
                public final class HstatsBuildMetadata {
                    private HstatsBuildMetadata() {}

                    /** Mod UUID from build-time env {@code AETHERHAVEN_HSTATS_MOD_UUID} or Gradle {@code hstats_mod_uuid}; empty when unset. */
                    public static final String HSTATS_MOD_UUID = "$escaped";
                }
                """.trimIndent()
            )
        }
    }

sourceSets.named("main") {
    java.srcDir(layout.buildDirectory.dir("generated/sources/hstats/java"))
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(generateHstatsBuildMetadata)
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(generateHstatsBuildMetadata)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:removal", "-Xlint:unchecked"))
}

tasks.test {
    useJUnitPlatform {
        includeTags("wall-placement")
    }
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

/** Wall wand directional tests; writes build/wall-placement-test-report.txt */
tasks.register<Test>("wallPlacementTests") {
    group = "verification"
    description = "Runs wall placement chaining unit tests; report at build/wall-placement-test-report.txt"
    dependsOn(tasks.testClasses)
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("wall-placement")
    }
    val reportFile = layout.buildDirectory.file("wall-placement-test-report.txt")
    doFirst {
        reportFile.get().asFile.parentFile.mkdirs()
        reportFile.get().asFile.writeText("Wall placement chain tests\n${"=".repeat(60)}\n")
    }
    val listener =
        object : org.gradle.api.tasks.testing.TestListener {
            override fun beforeSuite(suite: org.gradle.api.tasks.testing.TestDescriptor) {}

            override fun afterSuite(suite: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) {
                if (suite.parent == null) {
                    reportFile.get().asFile.appendText(
                        "\nTotal: ${result.testCount}  passed: ${result.successfulTestCount}  failed: ${result.failedTestCount}  skipped: ${result.skippedTestCount}\n"
                    )
                }
            }

            override fun beforeTest(testDescriptor: org.gradle.api.tasks.testing.TestDescriptor) {}

            override fun afterTest(testDescriptor: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) {
                val line =
                    "${result.resultType}: ${testDescriptor.className}.${testDescriptor.name}\n"
                reportFile.get().asFile.appendText(line)
                if (result.resultType == org.gradle.api.tasks.testing.TestResult.ResultType.FAILURE) {
                    reportFile.get().asFile.appendText(result.exceptions.joinToString("\n") { it.toString() } + "\n")
                }
            }
        }
    addTestListener(listener)
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
    doLast {
        logger.lifecycle("Wall placement test report: ${reportFile.get().asFile.absolutePath}")
    }
}

tasks.named<ProcessResources>("processResources") {
    var replaceProperties = mapOf(
        "plugin_group" to findProperty("plugin_group"),
        "plugin_maven_group" to project.group,
        "plugin_name" to project.name,
        "plugin_version" to project.version,
        "server_version" to findProperty("server_version"),

        "plugin_description" to findProperty("plugin_description"),
        "plugin_website" to findProperty("plugin_website"),

        "plugin_main_entrypoint" to findProperty("plugin_main_entrypoint"),
        "plugin_author" to findProperty("plugin_author")
    )

    filesMatching("manifest.json") {
        expand(replaceProperties)
    }

    inputs.properties(replaceProperties)
}

tasks.withType<Jar> {
    manifest {
        attributes["Specification-Title"] = rootProject.name
        attributes["Specification-Version"] = version
        attributes["Implementation-Title"] = project.name
        attributes["Implementation-Version"] =
            providers.environmentVariable("COMMIT_SHA_SHORT")
                .map { "${version}-${it}" }
                .getOrElse(version.toString())
    }
}

publishing {
    repositories {
        // This is where you put repositories that you want to publish to.
        // Do NOT put repositories for your dependencies here.
    }

    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

// IDEA no longer automatically downloads sources/javadoc jars for dependencies, so we need to explicitly enable the behavior.
idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

val syncAssets = tasks.register<Copy>("syncAssets") {
    group = "hytale"
    description = "Automatically syncs assets from Build back to Source after server stops."

    // Take from the temporary build folder (Where the game saved changes)
    from(layout.buildDirectory.dir("resources/main"))

    // Copy into your actual project source (Where your code lives)
    into("src/main/resources")

    // IMPORTANT: Protect the manifest template from being overwritten
    exclude("manifest.json")

    // If a file exists, overwrite it with the new version from the game
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    doLast {
        println("✅ Assets successfully synced from Game to Source Code!")
    }
}

afterEvaluate {
    val runServerTask = tasks.findByName("runServer") ?: tasks.findByName("server")
    if (runServerTask == null) {
        logger.warn("⚠️ Could not find 'runServer' or 'server' task (hytale-mod). syncAssets not hooked.")
        return@afterEvaluate
    }
    if (runServerTask !is JavaExec) {
        logger.warn("⚠️ Task '${runServerTask.name}' is not JavaExec; skipping sync hook and runServerNoSync.")
        return@afterEvaluate
    }
    val runServer = runServerTask as JavaExec
    // hytale-mod 0.7.x always adds an empty jvmArg when HytaleServer.aot is missing; on Windows Gradle's
    // JavaExec then fails with "Could not find or load main class" (empty ClassNotFoundException).
    runServer.jvmArgs = runServer.jvmArgs.filter { it.isNotBlank() }
    runServer.finalizedBy(syncAssets)
    logger.lifecycle("✅ Task '${runServer.name}' finalized by syncAssets (copy build resources back to src on exit).")

    tasks.register<JavaExec>("runServerNoSync") {
        group = "hytale"
        description =
            "Same as runServer but does not run syncAssets afterward — safe when you edit src/main/resources while testing."
        classpath = runServer.classpath
        mainClass = runServer.mainClass
        mainModule = runServer.mainModule
        modularity.inferModulePath = runServer.modularity.inferModulePath
        jvmArgs = runServer.jvmArgs.filter { it.isNotBlank() }
        workingDir = runServer.workingDir
        jvmArgs = runServer.jvmArgs
        args = runServer.args
        systemProperties = runServer.systemProperties
        environment = runServer.environment
        standardInput = runServer.standardInput
        isIgnoreExitValue = runServer.isIgnoreExitValue
        javaLauncher = runServer.javaLauncher
        enableAssertions = runServer.enableAssertions
    }
    logger.lifecycle("✅ Task 'runServerNoSync' registered (no post-exit asset sync).")
}
