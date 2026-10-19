import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Scheduling logic with no Android dependency, so it builds and tests anywhere.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "initiative"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnit()
}
