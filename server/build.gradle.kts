plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation("io.netty:netty-all:4.1.110.Final")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.1")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("org.mockito:mockito-core:5.11.0")
}

application {
    mainClass.set("net.limitmedia.pong.server.ServerLauncher")
}
