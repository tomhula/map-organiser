plugins {
    kotlin("jvm") version "2.1.20"
}

group = "io.github.tomhula"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("io.github.tomhula:orisclient-jvm:0.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("io.github.g0dkar:qrcode-kotlin:4.4.1")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("org.freemarker:freemarker:2.3.34")
    implementation("io.ktor:ktor-client-core:3.1.3")
    implementation("io.ktor:ktor-client-java:3.1.3")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
