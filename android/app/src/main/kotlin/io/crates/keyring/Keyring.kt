package io.crates.keyring

import android.content.Context
import java.io.File

/**
 * Required by the `android-native-keyring-store` Rust crate (a
 * `keyring`-crate backend `macula-rust-sdk`'s `KeyringStore` uses on
 * Android) — its own JNI export, `initializeNdkContext`, is only
 * reachable if a class with this exact package/name/signature exists on
 * the Kotlin side (`Java_io_crates_keyring_Keyring_00024Companion_initializeNdkContext`,
 * confirmed against the crate's own published docs). This name is fixed
 * by JNI's own symbol-mangling scheme — do not rename `initializeNdkContext`
 * or nest it differently, or the runtime linker will look for a symbol
 * that doesn't exist in the compiled library.
 *
 * Deliberately does NOT `System.loadLibrary` in an `init` block here, on
 * purpose, after checking the real build output: `android-native-keyring-store`
 * does not statically link into `libmacula_rust_sdk_ffi.so` — the actual
 * `scripts/build-rust-android.sh` run for this migration produced THREE
 * separate, per-ABI, content-hash-suffixed libraries alongside it
 * (`libandroid_native_keyring_store-<hash>.so`, a different hash per
 * ABI — confirmed directly in `android/app/src/main/jniLibs/<abi>/`, not
 * assumed from the crate's own generic README example, which shows a
 * fixed, non-hash-suffixed name that does not match what actually gets
 * built here). Since the exact filename varies per build and isn't
 * knowable as a compile-time string constant,
 * [ensureAndroidNativeKeyringStoreInitialized] discovers and loads it at
 * runtime instead — call it once, before
 * [initializeNdkContext], from a `Context` that's available (see
 * [io.macula.cam2me.MainActivity.onCreate]).
 */
class Keyring {
    companion object {
        external fun initializeNdkContext(context: Context)
    }
}

/**
 * Finds and loads whichever `libandroid_native_keyring_store-*.so` this
 * build actually packaged for the running device's ABI, then calls
 * [Keyring.initializeNdkContext]. Idempotent — safe to call more than
 * once (e.g. across `onCreate` calls after a config change).
 */
@Volatile
private var androidNativeKeyringStoreLoaded = false

fun ensureAndroidNativeKeyringStoreInitialized(context: Context) {
    if (!androidNativeKeyringStoreLoaded) {
        synchronized(Keyring::class) {
            if (!androidNativeKeyringStoreLoaded) {
                val dir = File(context.applicationInfo.nativeLibraryDir)
                val lib = dir.listFiles { file -> file.name.startsWith("libandroid_native_keyring_store") }
                    ?.firstOrNull()
                    ?: throw IllegalStateException(
                        "android_native_keyring_store native library not found in $dir -- " +
                            "check scripts/build-rust-android.sh actually packaged it for this ABI",
                    )
                System.load(lib.absolutePath)
                androidNativeKeyringStoreLoaded = true
            }
        }
    }
    Keyring.initializeNdkContext(context)
}
