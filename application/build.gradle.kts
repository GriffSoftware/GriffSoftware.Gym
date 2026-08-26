plugins {
    alias(libs.plugins.kotlin.jvm)
}

/*
 * APPLICATION
 * Use cases orchestrating the domain. Still pure Kotlin — no Android, no Room, no Compose.
 */
kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":domain"))
    implementation(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
