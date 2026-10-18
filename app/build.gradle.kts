import com.android.build.api.artifact.SingleArtifact
import java.time.LocalDate

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    // Code namespace differs from the application ID because `in` is a Kotlin keyword.
    namespace = "app.signsetu"
    compileSdk = 37

    defaultConfig {
        applicationId = "in.signsetu"
        minSdk = 29
        targetSdk = 37
        versionCode = 3
        versionName = "0.3.0"
        buildConfigField("long", "BUILD_EPOCH_DAY", "${LocalDate.now().toEpochDay()}L")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The on-device assistant (D-24) runs only on 64-bit phones; the x86_64 build serves the emulator.
        externalNativeBuild { cmake { abiFilters += listOf("arm64-v8a", "x86_64"); arguments += "-DCMAKE_BUILD_TYPE=Release" } }
    }

    ndkVersion = "30.0.16248370"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.1"
        }
    }

    // The officer picks the app language in Settings, so every language must ship in every install.
    bundle { language { enableSplit = false } }

    buildTypes {
        release {
            // Officers' phones are 64-bit ARM; x86 libraries only serve the emulator in debug builds.
            ndk { abiFilters += listOf("arm64-v8a") }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // D-26: the second screen links over bonded Bluetooth by default; "nearby" swaps in Google Nearby Connections for
    // departments whose phones have Play services. Only "standard" is the reference build that keeps every rule.
    flavorDimensions += "transport"
    productFlavors {
        create("standard") {
            dimension = "transport"
            isDefault = true
        }
        create("nearby") {
            dimension = "transport"
            applicationIdSuffix = ".nearby"
            versionNameSuffix = "-nearby"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Security checks fail the release lint gate.
    lint { error += listOf("HardcodedDebugMode", "SetJavaScriptEnabled", "ExportedService", "AllowBackup") }

    // Android calls made in plain JVM tests return defaults instead of throwing (timing marks, log lines).
    testOptions { unitTests.isReturnDefaultValues = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Native libraries and models ship uncompressed, so they load straight from the APK at full precision.
    packaging { jniLibs { useLegacyPackaging = false } }

    androidResources {
        noCompress += listOf("webp", "ogg", "mp4", "task", "bin")
    }
}

// SEC-30, SEC-42, SEC-66: the build fails if any library brings a forbidden permission into the merged manifest.
// The nearby flavor needs scanning and, below Android 13, location for Nearby Connections; it keeps the rest.
androidComponents {
    onVariants { variant ->
        val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val standard = variant.flavorName != "nearby"
        val check = tasks.register("check${name}Permissions") {
            inputs.file(manifest)
            doLast {
                val always = listOf(
                    "android.permission.INTERNET",
                    "android.permission.RECORD_AUDIO",
                    "android.permission.ACCESS_BACKGROUND_LOCATION",
                )
                val discovery = listOf(
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.ACCESS_COARSE_LOCATION",
                    "android.permission.BLUETOOTH_SCAN",
                    "android.permission.BLUETOOTH_ADMIN",
                    "android.permission.BLUETOOTH_ADVERTISE",
                    "android.permission.NEARBY_WIFI_DEVICES",
                )
                val forbidden = if (standard) always + discovery else always
                val text = manifest.get().asFile.readText()
                val found = forbidden.filter { text.contains("\"$it\"") }
                if (found.isNotEmpty()) throw GradleException("Forbidden permissions in the merged manifest: $found")
            }
        }
        // Every task that packages the merged manifest, so neither an APK nor an AAB nor an install can skip the check.
        val packaging = setOf("assemble$name", "bundle$name", "install$name", "package$name", "package${name}Bundle")
        tasks.matching { it.name in packaging }.configureEach { dependsOn(check) }
    }
}

// The test APK's classes load first, so androidx.test's lite protobuf would hide the full one MediaPipe needs.
configurations.matching { it.name.endsWith("AndroidTestRuntimeClasspath") }.configureEach {
    exclude(group = "com.google.protobuf", module = "protobuf-javalite")
}

// SEC-50: every resolved version is pinned in gradle.lockfile; update with --write-locks.
dependencyLocking {
    lockAllConfigurations()
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)

    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.biometric)
    // biometric 1.1.0 brings a fragment library too old for permission requests through ActivityResult.
    implementation(libs.androidx.fragment)
    implementation(libs.media3.exoplayer)
    implementation(libs.pdfbox.android)
    constraints {
        // PdfBox-Android brings BouncyCastle 1.72, which has known critical vulnerabilities (OSV scan); 1.86 fixes them.
        implementation(libs.bouncycastle.prov)
        implementation(libs.bouncycastle.pkix)
        implementation(libs.bouncycastle.util)
    }
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.compose)
    implementation(libs.mediapipe.tasks.vision) {
        // This backend uploads MediaPipe usage statistics and brings INTERNET (SEC-30); a stub destination replaces it.
        exclude(group = "com.google.android.datatransport", module = "transport-backend-cct")
        // Its classes are compiled against full protobuf (Any.Builder.build() returning Any); the lite runtime lacks that method.
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
    }
    implementation(libs.protobuf.java)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    // Play services only in the nearby flavor; the standard build has none (D-26).
    "nearbyImplementation"(libs.play.services.nearby)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.compose.ui.test.junit4.accessibility)
}
