import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
    kotlin("multiplatform") version "1.4.32"
    kotlin("plugin.serialization") version "1.4.30"
    `maven-publish`
}

allprojects {
    group = "fr.acinq.lightning"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenLocal()
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        mavenCentral()
        google()
        jcenter()
    }
}

val currentOs = org.gradle.internal.os.OperatingSystem.current()

kotlin {
    val ktorVersion: String by extra { "1.5.3" }
    fun ktor(module: String) = "io.ktor:ktor-$module:$ktorVersion"
    val secp256k1Version = "0.5.2"
    val serializationVersion = "1.1.0" 
    val coroutineVersion = "1.4.3-native-mt"

    val commonMain by sourceSets.getting {
        dependencies {
            api("fr.acinq.bitcoin:bitcoin-kmp:0.7.1")
            api("fr.acinq.secp256k1:secp256k1-kmp:$secp256k1Version")
            api("org.kodein.log:kodein-log:0.10.1-utc")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutineVersion")
            api("org.jetbrains.kotlinx:kotlinx-serialization-core:$serializationVersion")
            api("org.jetbrains.kotlinx:kotlinx-serialization-cbor:$serializationVersion")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
        }
    }
    val commonTest by sourceSets.getting {
        dependencies {
            implementation(kotlin("test-common"))
            implementation(kotlin("test-annotations-common"))
            implementation("org.kodein.memory:kodein-memory-files:0.8.1")
            implementation(project(":lightning-kmp-test-fixtures"))
        }
    }

    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        compilations["main"].defaultSourceSet.dependencies {
            api(ktor("client-okhttp"))
            api(ktor("network"))
            api(ktor("network-tls"))
            implementation("org.slf4j:slf4j-api:1.7.29")
            api("org.xerial:sqlite-jdbc:3.32.3.2")
        }
        compilations["test"].defaultSourceSet.dependencies {
            val target = when {
                currentOs.isLinux -> "linux"
                currentOs.isMacOsX -> "darwin"
                currentOs.isWindows -> "mingw"
                else -> error("UnsupportedmOS $currentOs")
            }
            implementation("fr.acinq.secp256k1:secp256k1-kmp-jni-jvm-$target:$secp256k1Version")
            implementation(kotlin("test-junit"))
            implementation("org.bouncycastle:bcprov-jdk15on:1.64")
            implementation("ch.qos.logback:logback-classic:1.2.3")
            implementation("io.ktor:ktor-server-netty:$ktorVersion")
            implementation("io.ktor:ktor-serialization:$ktorVersion")
            implementation("com.typesafe:config:1.4.1")
        }
    }

    val nativeMain by sourceSets.creating { dependsOn(commonMain) }
    val nativeTest by sourceSets.creating { dependsOn(commonTest) }

    if (currentOs.isLinux) {
        linuxX64("linux") {
            compilations["main"].defaultSourceSet {
                dependsOn(nativeMain)
            }
            compilations["test"].defaultSourceSet {
                dependsOn(nativeTest)
                dependencies {
                    implementation(ktor("client-curl"))
                }
            }
        }
    }

    if (currentOs.isMacOsX) {
        ios {
            val platform = when (name) {
                "iosX64" -> "iphonesimulator"
                "iosArm64" -> "iphoneos"
                else -> error("Unsupported target $name")
            }

            compilations["main"].cinterops.create("ios_network_framework")
            compilations["main"].cinterops.create("PhoenixCrypto") {
                val interopTask = tasks[interopProcessingTaskName]
                interopTask.dependsOn(":PhoenixCrypto:buildCrypto${platform.capitalize()}")
                includeDirs.headerFilterOnly("$rootDir/PhoenixCrypto/build/Release-$platform/include")
            }
            compilations["main"].defaultSourceSet {
                dependsOn(nativeMain)
            }
            compilations["test"].defaultSourceSet {
                dependsOn(nativeTest)
                dependencies {
                    implementation(ktor("client-ios"))
                }
            }
        }
    }

    sourceSets.all {
        languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
        languageSettings.useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
    }

    // Configure all compilations of all targets:
    targets.all {
        compilations.all {
            kotlinOptions {
                allWarningsAsErrors = true
            }
        }
    }
}


// Disable cross compilation
afterEvaluate {
    val targets = when {
        currentOs.isLinux -> listOf()
        else -> listOf("linuxX64")
    }.mapNotNull { kotlin.targets.findByName(it) as? KotlinNativeTarget }

    configure(targets) {
        compilations.all {
            cinterops.all { tasks[interopProcessingTaskName].enabled = false }
            compileKotlinTask.enabled = false
            tasks[processResourcesTaskName].enabled = false
        }
        binaries.all { linkTask.enabled = false }

        mavenPublication {
            val publicationToDisable = this
            tasks.withType<AbstractPublishToMaven>().all { onlyIf { publication != publicationToDisable } }
            tasks.withType<GenerateModuleMetadata>().all { onlyIf { publication.get() != publicationToDisable } }
        }
    }
}

afterEvaluate {
    tasks.withType<AbstractTestTask>() {
        testLogging {
            events("passed", "skipped", "failed", "standard_out", "standard_error")
            showExceptions = true
            showStackTraces = true
        }
    }
    tasks.withType<org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest> {
        environment("TEST_RESOURCES_PATH", projectDir.resolve("src/commonTest/resources"))
    }

    tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeHostTest> {
        environment("TEST_RESOURCES_PATH", projectDir.resolve("src/commonTest/resources"))
    }

    tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest> {
        environment("SIMCTL_CHILD_TEST_RESOURCES_PATH", projectDir.resolve("src/commonTest/resources"))
    }
}

/*
Electrum integration test environment + tasks configuration
 */
val dockerTestEnv by tasks.creating(Exec::class) {
    workingDir = projectDir.resolve("docker-local-test")
    commandLine("bash", "env.sh", "remove", "net-create", "btc-create", "elx-create", "btc-start", "elx-start")
    doLast {
        gradle.buildFinished {
            exec {
                println("Cleaning up dockers...")
                workingDir = projectDir.resolve("docker-local-test")
                commandLine("bash", "env.sh", "elx-stop", "btc-stop", "remove")
            }
        }
    }
}

val includeIntegrationTests = project.findProperty("integrationTests") == "include"
tasks.withType<AbstractTestTask> {
    if (includeIntegrationTests) {
        dependsOn(dockerTestEnv)
    } else {
        filter.excludeTestsMatching("*IntegrationTest")
    }
}

// Linux native does not support integration tests (sockets are not implemented in Linux native)
if (currentOs.isLinux) {
    val linuxTest by tasks.getting(KotlinNativeTest::class) {
        filter.excludeTestsMatching("*IntegrationTest")
    }
}
