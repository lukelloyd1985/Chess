import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Firebase (Google sign-in + online games) is enabled only when the developer
// has dropped their own google-services.json next to this file. Without it the
// app still builds and runs; sign-in/online play show a "not configured" notice.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
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
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.github.lukelloyd1985.chess"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // Stockfish is built for 64-bit ABIs only.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets.getByName("main").assets.srcDir(nnueOutDir)

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    // The engine binary ships as lib/<abi>/libstockfish.so and is executed from
    // nativeLibraryDir, which requires the libraries to be extracted on install.
    packaging { jniLibs { useLegacyPackaging = true } }
    androidResources { noCompress += "nnue" }
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
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)
}
