plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * The OAuth client id of type *Web application*, not the Android one.
 *
 * Credential Manager sends it as `serverClientId` so that the ID token it returns is minted
 * for the backend's audience — an Android client id here produces a token the server is
 * right to reject.
 */
fun googleWebClientId(): String? =
    (project.findProperty("GRIFFGYM_GOOGLE_WEB_CLIENT_ID") as String?)
        ?: System.getenv("GRIFFGYM_GOOGLE_WEB_CLIENT_ID")

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
     *
     * The Google OAuth **web** client id is read the same way, from
     * GRIFFGYM_GOOGLE_WEB_CLIENT_ID. It is not a secret — it travels in every sign-in
     * request and the server checks the resulting token against it — but it is per
     * environment, so it belongs here rather than in source. Debug falls back to an empty
     * string: a developer without a Google Cloud project must still be able to build and
     * run everything else, and `GoogleSignInLauncher` turns the empty value into an honest
     * message rather than an obscure Credential Manager failure. A *release* build with no
     * client id is a different matter — a dead button on the entry screen — and is refused
     * outright by the guard below.
     */
    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/\"")
            buildConfigField("boolean", "HTTP_LOGGING_ENABLED", "true")
            buildConfigField(
                "String",
                "GOOGLE_WEB_CLIENT_ID",
                "\"${googleWebClientId().orEmpty()}\"",
            )
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
            buildConfigField(
                "String",
                "GOOGLE_WEB_CLIENT_ID",
                "\"${googleWebClientId().orEmpty()}\"",
            )
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

/*
 * The release guard, deliberately at execution time rather than configuration time.
 *
 * A `require` in the `release { }` block would be evaluated while Gradle configures *any*
 * build, so an unset client id would refuse to build a debug APK as well — which is exactly
 * the situation a developer without a Google Cloud project is in. Hanging it off the task
 * that writes the release BuildConfig means it fires when a release is genuinely being
 * produced, and stays out of the way otherwise.
 *
 * The value is read here rather than inside the action so no build-script state is captured,
 * which keeps the check configuration-cache safe.
 */
val configuredGoogleWebClientId: String? = googleWebClientId()

tasks.matching { it.name == "generateReleaseBuildConfig" }.configureEach {
    doFirst {
        require(!configuredGoogleWebClientId.isNullOrBlank()) {
            "GRIFFGYM_GOOGLE_WEB_CLIENT_ID must be set for a release build: without it the " +
                "Sign in with Google button on the entry screen cannot work. Pass " +
                "-PGRIFFGYM_GOOGLE_WEB_CLIENT_ID=<web client id> or set the environment " +
                "variable of the same name."
        }
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
