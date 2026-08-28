#!/usr/bin/env bash
# Cross-compiles macula-rust-sdk-ffi for every Android ABI, regenerates the
# Kotlin bindings, and copies both into the Gradle project.
#
# Requires: Android NDK (ANDROID_NDK_HOME set), cargo-ndk
# (`cargo install cargo-ndk`), and the four Android Rust targets
# (`rustup target add aarch64-linux-android armv7-linux-androideabi
# i686-linux-android x86_64-linux-android`).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FFI_CRATE_DIR="$REPO_ROOT/rust/macula-rust-sdk"
JNI_LIBS_DIR="$REPO_ROOT/android/app/src/main/jniLibs"
KOTLIN_SRC_ROOT="$REPO_ROOT/android/app/src/main/kotlin"
# uniffi.toml's [bindings.kotlin] package_name = "io.macula.sdk" -- the
# generated file always lands under that package path relative to
# --out-dir, so this must track that setting exactly, not our own app's
# package (io.macula.cam2me).
KOTLIN_OUT_DIR="$KOTLIN_SRC_ROOT/io/macula/sdk"

ABIS=(arm64-v8a armeabi-v7a x86_64)

if ! command -v cargo-ndk >/dev/null 2>&1; then
    echo "cargo-ndk not found. Install with: cargo install cargo-ndk" >&2
    exit 1
fi

echo "==> Building macula-rust-sdk-ffi for: ${ABIS[*]}"
mkdir -p "$JNI_LIBS_DIR"
(
    cd "$FFI_CRATE_DIR"
    cargo ndk \
        --target arm64-v8a --target armeabi-v7a --target x86_64 \
        --output-dir "$JNI_LIBS_DIR" \
        build --release -p macula-rust-sdk-ffi
)

echo "==> Regenerating Kotlin bindings"
rm -rf "$KOTLIN_OUT_DIR"
mkdir -p "$KOTLIN_SRC_ROOT"
(
    cd "$FFI_CRATE_DIR"
    cargo run -p macula-rust-sdk-ffi --release --bin uniffi-bindgen -- generate \
        --library "$JNI_LIBS_DIR/arm64-v8a/libmacula_rust_sdk_ffi.so" \
        --language kotlin \
        --out-dir "$KOTLIN_SRC_ROOT" \
        --no-format
)

echo "==> Done. jniLibs: $JNI_LIBS_DIR"
echo "==> Done. Kotlin bindings: $KOTLIN_OUT_DIR"
