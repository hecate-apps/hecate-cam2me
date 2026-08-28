plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "social.hecate.cam2me"
    // compose-bom's own transitive deps (material-ripple-android 1.12.0)
    // require API 37+ to compile against -- caught by CI's
    // checkDebugAarMetadata failing outright with 36, not assumed.
    compileSdk = 37

    defaultConfig {
        applicationId = "social.hecate.cam2me"
        minSdk = 26
        targetSdk = 37
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

    lint {
        // The generated bindings (io/macula/sdk/) guard their one API-33+
        // call (java.lang.ref.Cleaner#create) behind a runtime
        // Class.forName/catch-ClassNotFoundException check, falling back
        // to a JNA-based cleaner below minSdk 33 -- genuinely safe, but
        // Lint's static NewApi check can't see through reflection-based
        // guards and flags it as an outright violation regardless.
        // Verified directly in the generated source, not assumed.
        disable += "NewApi"
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

    // Local storage for paired contacts (node_id, display name, last
    // known station/online state) -- structured, queryable data that
    // will grow, unlike simple settings (station URL etc.), which will
    // use DataStore separately when that lands.
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
}

ksp {
    // Keeps Room's schema-history JSON alongside the code, the
    // documented default for exportSchema = true -- otherwise Room
    // emits a build warning every compile.
    arg("room.schemaLocation", "$projectDir/schemas")
}
