plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ottotech.workoutbuilder"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.ottotech.workoutbuilder"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged resources to draw real screens.
            isIncludeAndroidResources = true
            // Screenshots are slow, so they only run on request: `testDebugUnitTest -Pscreenshots`
            // runs them alone, a plain test run leaves them out.
            all { test ->
                if (project.hasProperty("screenshots")) {
                    test.filter.includeTestsMatching("*.screenshots.*")
                    test.systemProperty("roborazzi.test.record", "true")
                    // Robolectric reaches into JDK internals that newer JDKs keep closed.
                    test.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
                    test.outputs.upToDateWhen { false }
                } else {
                    test.exclude("**/screenshots/**")
                }
            }
        }
    }

    // The SDK pulls in Apache httpclient5/httpcore5, whose duplicate META-INF files otherwise
    // fail mergeDebugJavaResource.
    packaging {
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Official Anthropic SDK (Java; Kotlin uses it directly). Verified on Android 2026-09-24.
    // Also supplies Jackson, which CoachResponseMapper parses with.
    implementation("com.anthropic:anthropic-java:2.34.0")

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
}
