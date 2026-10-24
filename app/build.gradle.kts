import com.android.build.api.artifact.SingleArtifact
import java.time.LocalDate
import java.util.Properties

// Local settings, untracked: -P properties win, then local.properties. Empty is fine and means offline only.
val local = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load) }
// Direct online assistant key: env OPENROUTER_API_KEY, else openrouterKey.
val openrouterKey: String = System.getenv("OPENROUTER_API_KEY") ?: local.getProperty("openrouterKey", "")
val gatewayUrl: String = providers.gradleProperty("gatewayUrl").orNull ?: local.getProperty("gatewayUrl", "")
// The client name sent to the assistant service; local.properties may set the one a service requires.
val assistUserAgent: String = local.getProperty("assistUserAgent", "SignSetu")

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
        versionCode = 9
        versionName = "0.6.0"
        buildConfigField("long", "BUILD_EPOCH_DAY", "${LocalDate.now().toEpochDay()}L")
        // The department's gateway: -PgatewayUrl=https://... or gatewayUrl in local.properties.
        buildConfigField("String", "GATEWAY_URL", "\"$gatewayUrl\"")
        buildConfigField("String", "ASSIST_KEY", "\"$openrouterKey\"")
        buildConfigField("String", "ASSIST_URL", "\"https://agentrouter.org/v1/responses\"")
        buildConfigField("String", "ASSIST_MODEL", "\"gpt-5.6-sol\"")
        buildConfigField("String", "ASSIST_UA", "\"$assistUserAgent\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The on-device assistant runs only on 64-bit phones; the x86_64 build serves the emulator.
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
            // Universal 64-bit build by default (ARM phones, x86_64 emulators and tablets); -Pabi=arm64-v8a for phones only.
            ndk { abiFilters += (providers.gradleProperty("abi").orNull?.split(",") ?: listOf("arm64-v8a", "x86_64")) }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // the second screen links over bonded Bluetooth by default; "nearby" swaps in Google Nearby Connections for
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

// INTERNET is allowed only for the department gateway (CloudAi). the build fails if any library brings a forbidden permission into the merged manifest.
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
        // A release APK is public: a key compiled into it can be read by anyone who has the file.
        val keyCheck = tasks.register("check${name}NoEmbeddedKey") {
            val embedded = openrouterKey.isNotBlank() && variant.buildType == "release" && !providers.gradleProperty("allowEmbeddedKey").isPresent
            doLast {
                if (embedded) throw GradleException("A release build must not embed an assistant key. Use -PgatewayUrl, or pass -PallowEmbeddedKey for a private build.")
            }
        }
        // Every task that packages the merged manifest, so neither an APK nor an AAB nor an install can skip the check.
        val packaging = setOf("assemble$name", "bundle$name", "install$name", "package$name", "package${name}Bundle")
        tasks.matching { it.name in packaging }.configureEach { dependsOn(check, keyCheck) }
    }
}

// every resolved version is pinned in gradle.lockfile; update with --write-locks.
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
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)

    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
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
        // This backend uploads MediaPipe usage statistics and brings INTERNET; a stub destination replaces it.
        exclude(group = "com.google.android.datatransport", module = "transport-backend-cct")
        // Only ImageEmbedder uses this 20 MB on-device LLM runtime; the holistic landmarker never loads it.
        exclude(group = "com.google.ai.edge.litertlm", module = "litertlm-android")
    }
    // MediaPipe 1.1.1 is built on the lite runtime; its own 4.26.1 pin has CVE-2024-7254.
    implementation(libs.protobuf.javalite)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    // Play services only in the nearby flavor; the standard build has none.
    "nearbyImplementation"(libs.play.services.nearby)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.compose.ui.test.junit4.accessibility)
}
