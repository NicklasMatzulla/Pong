plugins {
    `java-library`
}

dependencies {
    api("org.jmonkeyengine:jme3-core:3.6.1-stable")
    api("org.jmonkeyengine:jme3-effects:3.6.1-stable")
    api("com.google.code.gson:gson:2.11.0")
    api("org.slf4j:slf4j-api:2.0.13")
    api("com.fasterxml.jackson.core:jackson-annotations:2.17.1")
    api("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.1")
    implementation("org.apache.commons:commons-math3:3.6.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.3")
    testImplementation("org.mockito:mockito-core:5.11.0")
}
