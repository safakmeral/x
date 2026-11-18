// Pure Kotlin/JVM module: no Android dependency, so its unit tests run fast on the JVM.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // G-34/G-35: strict JSON for the proxy contract and the model answer (tree API only).
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
