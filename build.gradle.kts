import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    `java-library`
}

val javaVersion = JavaLanguageVersion.of(21)

allprojects {
    group = "net.limitmedia.pong"
    version = "0.2.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(javaVersion)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events = setOf(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        }
    }

    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.slf4j" && requested.name == "slf4j-simple") {
                useTarget("ch.qos.logback:logback-classic:1.5.6")
            }
        }
    }
}

tasks.register<JavaExec>("runServer") {
    group = "application"
    description = "Starts the dedicated Pong server"
    mainClass.set("net.limitmedia.pong.server.ServerLauncher")
    classpath = project(":server").configurations.getByName("runtimeClasspath")
    dependsOn(":server:classes")
}

tasks.register<JavaExec>("runClient") {
    group = "application"
    description = "Starts the Pong client"
    mainClass.set("net.limitmedia.pong.client.ClientLauncher")
    classpath = project(":client").configurations.getByName("runtimeClasspath")
    dependsOn(":client:classes")
}
