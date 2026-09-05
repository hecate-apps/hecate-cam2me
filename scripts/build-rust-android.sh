#!/usr/bin/env bash
# Cross-compiles cam2me-ffi (cam2me's own top-level build target for
# macula-rust-ffi -- see rust/cam2me-ffi/src/lib.rs for why that
# indirection exists) for every Android ABI, regenerates the Kotlin
# bindings, and copies both into the Gradle project.
#
# Requires: Android NDK (ANDROID_NDK_HOME set), cargo-ndk
# (`cargo install cargo-ndk`), and the four Android Rust targets
# (`rustup target add aarch64-linux-android armv7-linux-androideabi
# i686-linux-android x86_64-linux-android`).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FFI_CRATE_DIR="$REPO_ROOT/rust/cam2me-ffi"
JNI_LIBS_DIR="$REPO_ROOT/android/app/src/main/jniLibs"
KOTLIN_SRC_ROOT="$REPO_ROOT/android/app/src/main/kotlin"
# uniffi.toml's [bindings.kotlin] package_name = "io.macula.sdk" -- the
# generated file always lands under that package path relative to
# --out-dir, so this must track that setting exactly, not our own app's
# package (io.macula.cam2me).
KOTLIN_OUT_DIR="$KOTLIN_SRC_ROOT/io/macula/sdk"

# cam2me-ffi's own [lib] name is "cam2me_ffi", NOT "macula_rust_ffi" --
# giving it the identical name to macula-rust-ffi's own [lib] triggers a
# real Cargo "output filename collision" warning (see rust/cam2me-ffi/
# Cargo.toml for the full explanation). But the generated Kotlin bindings
# call System.loadLibrary("macula_rust_ffi") regardless -- that name comes
# from macula-rust-ffi's own uniffi.toml cdylib_name setting, baked into
# the bindings at generation time, independent of what this crate's build
# artifact is actually named. So the raw cargo-ndk output
# (libcam2me_ffi.so) must be renamed to libmacula_rust_ffi.so per ABI
# before it ships in jniLibs/ -- otherwise the app builds fine and fails
# at runtime with an UnsatisfiedLinkError the first time Kotlin code
# touches the SDK.
BUILT_LIB_NAME="libcam2me_ffi"
RUNTIME_LIB_NAME="libmacula_rust_ffi"

ABIS=(arm64-v8a armeabi-v7a x86_64)

if ! command -v cargo-ndk >/dev/null 2>&1; then
    echo "cargo-ndk not found. Install with: cargo install cargo-ndk" >&2
    exit 1
fi

echo "==> Building cam2me-ffi for: ${ABIS[*]}"
mkdir -p "$JNI_LIBS_DIR"
(
    cd "$FFI_CRATE_DIR"
    cargo ndk \
        --target arm64-v8a --target armeabi-v7a --target x86_64 \
        --output-dir "$JNI_LIBS_DIR" \
        build --release
)

echo "==> Renaming $BUILT_LIB_NAME.so -> $RUNTIME_LIB_NAME.so per ABI"
for abi in "${ABIS[@]}"; do
    mv -f "$JNI_LIBS_DIR/$abi/$BUILT_LIB_NAME.so" "$JNI_LIBS_DIR/$abi/$RUNTIME_LIB_NAME.so"
done

echo "==> Regenerating Kotlin bindings"
rm -rf "$KOTLIN_OUT_DIR"
mkdir -p "$KOTLIN_SRC_ROOT"
(
    cd "$FFI_CRATE_DIR"
    cargo run --release --bin uniffi-bindgen -- generate \
        --library "$JNI_LIBS_DIR/arm64-v8a/$RUNTIME_LIB_NAME.so" \
        --language kotlin \
        --out-dir "$KOTLIN_SRC_ROOT" \
        --no-format
)

echo "==> Done. jniLibs: $JNI_LIBS_DIR"
echo "==> Done. Kotlin bindings: $KOTLIN_OUT_DIR"
