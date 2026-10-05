import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    // Kotlin compiles via AGP's built-in Kotlin support - no kotlin-android plugin.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.play.publisher)
}

// Stockfish 19 needs its NNUE network file. It is downloaded at build time (the
// file name contains the first 12 hex chars of its SHA-256, which we verify) and
// bundled as an asset. Drop a copy in app/nnue/ to build offline.
val nnueName = "nn-1a298aa575a0.nnue"
val nnueOutDir = layout.buildDirectory.dir("generated/nnue")

val downloadNnue by tasks.registering {
    val outFile = nnueOutDir.map { it.file(nnueName) }
    val localCopy = file("nnue/$nnueName")
    outputs.file(outFile)
    doLast {
        val target = outFile.get().asFile
        target.parentFile.mkdirs()
        fun valid(f: File): Boolean {
            if (!f.exists()) return false
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            return hex.startsWith(nnueName.removePrefix("nn-").removeSuffix(".nnue"))
        }
        if (valid(target)) return@doLast
        if (localCopy.exists()) {
            localCopy.copyTo(target, overwrite = true)
        } else {
            val urls = listOf(
                "https://tests.stockfishchess.org/api/nn/$nnueName",
                "https://data.stockfishchess.org/nn/$nnueName",
            )
            var ok = false
            for (u in urls) {
                try {
                    uri(u).toURL().openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
                    if (valid(target)) { ok = true; break }
                } catch (e: Exception) {
                    logger.warn("Could not download $u: ${e.message}")
                }
            }
            if (!ok) throw GradleException("Could not obtain $nnueName. Download it from https://tests.stockfishchess.org/api/nn/$nnueName into app/nnue/ and rebuild.")
        }
        if (!valid(target)) throw GradleException("$nnueName failed its checksum")
    }
}

android {
    namespace = "com.github.lukelloyd1985.chess"
    // 36 (not 37): API 37 isn't an installable stable SDK platform yet.
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.github.lukelloyd1985.chess"
        minSdk = 26
        targetSdk = 36
        // Play rejects non-increasing versionCodes; the CI run number is a monotonic source.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        // The release workflow sets this to the git tag (e.g. "v1.2.3").
        versionName = System.getenv("RELEASE_VERSION_NAME") ?: "1.0.0-dev"

        // Stockfish is built for 64-bit ABIs only.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }

        // The Appwrite project ID isn't sensitive (it only identifies the project and ships inside
        // the APK regardless), so it is read from appwrite/appwrite.json - the same file the deploy
        // workflow pushes from - instead of being duplicated in a secret.
        val appwriteProjectId = Regex("\"projectId\"\\s*:\\s*\"([^\"]*)\"")
            .find(rootProject.file("appwrite/appwrite.json").readText())
            ?.groupValues?.get(1) ?: ""

        // The remaining IDs are fixed names this codebase chose, matching appwrite.json's $id fields.
        buildConfigField("String", "APPWRITE_ENDPOINT", "\"${System.getenv("APPWRITE_ENDPOINT") ?: "https://cloud.appwrite.io/v1"}\"")
        buildConfigField("String", "APPWRITE_PROJECT_ID", "\"$appwriteProjectId\"")
        buildConfigField("String", "APPWRITE_DATABASE_ID", "\"${System.getenv("APPWRITE_DATABASE_ID") ?: "chess"}\"")
        buildConfigField("String", "APPWRITE_COLLECTION_GAMES_ID", "\"${System.getenv("APPWRITE_COLLECTION_GAMES_ID") ?: "games"}\"")
        buildConfigField("String", "APPWRITE_FUNCTION_MAINTENANCE_ID", "\"${System.getenv("APPWRITE_FUNCTION_MAINTENANCE_ID") ?: "maintenance"}\"")

        // Google Cloud OAuth 2.0 *Web application* Client ID. Credential Manager mints the ID token
        // for it, and the maintenance Function verifies that token's audience against the same
        // value. Not a secret.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${System.getenv("GOOGLE_WEB_CLIENT_ID") ?: ""}\"")
    }

    signingConfigs {
        create("release") {
            // Store and key password are the same (PKCS12 keystores have a single password).
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = "chess"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
        // CI runners are fresh VMs, so AGP would generate a new random debug keystore on every
        // build and Google Sign-In would reject the ever-changing certificate. A stable keystore
        // from CI lets its SHA-1 be registered with Google once.
        getByName("debug") {
            val keystorePath = System.getenv("DEBUG_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("DEBUG_KEYSTORE_PASSWORD")
                keyAlias = "chessdebug"
                keyPassword = System.getenv("DEBUG_KEYSTORE_PASSWORD")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // AGP 9 rejects Provider instances in the SourceSet API, so pass the plain directory. The
    // task dependency is carried by the merge*Assets wiring on downloadNnue below.
    sourceSets.getByName("main").assets.srcDir(nnueOutDir.get().asFile)

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val keystorePath = System.getenv("KEYSTORE_PATH")
            // Without release-signing secrets fall back to the debug keystore so a testable APK is still produced.
            signingConfig = if (!keystorePath.isNullOrBlank()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The engine binary ships as lib/<abi>/libstockfish.so and is executed from
    // nativeLibraryDir, which requires the libraries to be extracted on install.
    packaging { jniLibs { useLegacyPackaging = true } }
    androidResources { noCompress += "nnue" }
}

// Publishes the release bundle to Play's closed testing track via `publishReleaseBundle`.
// Only running a publish task needs credentials (ANDROID_PUBLISHER_CREDENTIALS).
play {
    track.set("alpha")
    releaseStatus.set(com.github.triplet.gradle.androidpublisher.ReleaseStatus.COMPLETED)
    defaultToAppBundles.set(true)
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(downloadNnue)
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.appwrite)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)
}
