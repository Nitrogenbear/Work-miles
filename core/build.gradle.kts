import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin logic (tax years, HMRC rates, distance, CSV) with no Android
// dependencies, so it can be unit-tested on any JVM.
plugins {
    kotlin("jvm") version "2.1.0"
}

group = "uk.co.mheonsitetraining.miles"
version = "1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
