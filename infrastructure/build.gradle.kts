plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

/*
 * INFRASTRUCTURE
 * Room database, entities, DAOs, mappers, repository implementations and seeding.
 * Depends on :domain only — never on :presentation.
 */
android {
    namespace = "com.griffgym.infrastructure"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    /*
     * The API base URL is configuration, not a constant in the source.
     *
     * Debug points at 10.0.2.2, which is how the emulator reaches the host machine's
     * localhost. Release has no default at all: it reads GRIFFGYM_API_BASE_URL from the
     * Gradle properties or the environment, so shipping a build without deciding where it
     * talks to is a build failure rather than a surprise in production.
     */
    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/\"")
            buildConfigField("boolean", "HTTP_LOGGING_ENABLED", "true")
        }
        release {
            val releaseBaseUrl = (project.findProperty("GRIFFGYM_API_BASE_URL") as String?)
                ?: System.getenv("GRIFFGYM_API_BASE_URL")
                ?: "https://api.griffgym.invalid/"

            require(releaseBaseUrl.startsWith("https://")) {
                "GRIFFGYM_API_BASE_URL must be HTTPS for a release build, was '$releaseBaseUrl'."
            }

            buildConfigField("String", "API_BASE_URL", "\"$releaseBaseUrl\"")
            buildConfigField("boolean", "HTTP_LOGGING_ENABLED", "false")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.generateKotlin", "true")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // MigrationTestHelper loads the exported schemas through the asset loader, so the
    // directory KSP writes them to has to be visible to the unit tests. Test-only: the
    // schemas are a development artefact and never ship inside the app.
    sourceSets.getByName("test") {
        assets.srcDir(layout.projectDirectory.dir("schemas"))
    }
}

dependencies {
    api(project(":domain"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.hilt.android)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.work.testing)

    // The module declares an instrumentation runner but ships no androidTest sources — every
    // test here runs on Robolectric so that `./gradlew test` covers them without a device.
    // Without the runner on the classpath the empty instrumentation APK crashes on launch, so
    // `connectedAndroidTest` reported a failure for a suite that does not exist.
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
