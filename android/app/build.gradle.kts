plugins {
    id("com.android.application")
}

android {
    namespace = "social.hecate.cam2me"
    compileSdk = 36

    defaultConfig {
        applicationId = "social.hecate.cam2me"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // Populated by ../../scripts/build-rust-android.sh, which cross-compiles
    // macula-rust-sdk-ffi via cargo-ndk and drops one libmacula_rust_sdk_ffi.so
    // per ABI here. Not committed -- build artifacts, regenerated locally or
    // in CI before this module ever compiles.
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // Generated Kotlin bindings (io.macula.sdk package, under
    // src/main/kotlin/io/macula/sdk/) call into the native lib above via
    // JNA, and every async method (all of them, on FfiSession/FfiStream)
    // is a Kotlin suspend fun bridged through kotlinx.coroutines --
    // confirmed by inspecting the real generated file's own imports, not
    // assumed. Both are runtime requirements of the generated code
    // itself, not a choice made here.
    implementation("net.java.dev.jna:jna:5.17.0@aar")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
