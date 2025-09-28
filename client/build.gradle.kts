plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

dependencies {
    implementation(project(":core"))
    implementation("org.jmonkeyengine:jme3-lwjgl3:3.6.1-stable")
    implementation("org.jmonkeyengine:jme3-desktop:3.6.1-stable")
    implementation("org.jmonkeyengine:jme3-jogg:3.6.1-stable")
    implementation("org.jmonkeyengine:jme3-plugins:3.6.1-stable")
    implementation("org.jmonkeyengine:jme3-effects:3.6.1-stable")
    implementation("io.netty:netty-all:4.1.110.Final")
    implementation("org.openjfx:javafx-controls:23")
    implementation("org.openjfx:javafx-graphics:23")
    implementation("org.openjfx:javafx-base:23")
    implementation("net.onedaybeard.artemis:artemis-odb:2.3.0")
    implementation("ch.qos.logback:logback-classic:1.5.6")
    testImplementation(project(":core"))
    testImplementation("org.jmonkeyengine:jme3-core:3.6.1-stable")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

javafx {
    version = "23"
    modules = listOf("javafx.controls", "javafx.graphics", "javafx.base")
}

application {
    mainClass.set("net.limitmedia.pong.client.ClientLauncher")
}
