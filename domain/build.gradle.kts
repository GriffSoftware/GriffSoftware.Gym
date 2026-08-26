plugins {
    alias(libs.plugins.kotlin.jvm)
}

/*
 * DOMAIN
 * Pure Kotlin. Knows nothing about Android, Compose, Room or any framework.
 * Contains domain models, value objects, business rules and repository contracts.
 */
kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
