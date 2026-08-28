#!/usr/bin/env bash
# Cross-compiles macula-rust-sdk-ffi for iOS (device + simulator, both
# architectures), packages an XCFramework, regenerates the Swift
# bindings, and copies both into the Xcode project.
#
# Requires: macOS with Xcode (for `xcodebuild -create-xcframework`), and
# the iOS Rust targets (`rustup target add aarch64-apple-ios
# aarch64-apple-ios-sim x86_64-apple-ios`).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FFI_CRATE_DIR="$REPO_ROOT/rust/macula-rust-sdk"
SWIFT_OUT_DIR="$REPO_ROOT/ios/MaculaCam2Me/ffi"
XCFRAMEWORK_OUT="$REPO_ROOT/ios/MaculaRustSdkFFI.xcframework"
LIB_NAME="libmacula_rust_sdk_ffi.a"

if [[ "$(uname)" != "Darwin" ]]; then
    echo "This script must run on macOS (xcodebuild -create-xcframework is Apple-only)." >&2
    exit 1
fi

echo "==> Building macula-rust-sdk-ffi for iOS device + simulator"
(
    cd "$FFI_CRATE_DIR"
    cargo build --release -p macula-rust-sdk-ffi --target aarch64-apple-ios
    cargo build --release -p macula-rust-sdk-ffi --target aarch64-apple-ios-sim
    cargo build --release -p macula-rust-sdk-ffi --target x86_64-apple-ios
)

TARGET_DIR="$FFI_CRATE_DIR/target"
SIM_LIB_DIR="$(mktemp -d)"
trap 'rm -rf "$SIM_LIB_DIR"' EXIT

echo "==> Building a universal simulator (arm64 + x86_64) static lib via lipo"
lipo -create \
    "$TARGET_DIR/aarch64-apple-ios-sim/release/$LIB_NAME" \
    "$TARGET_DIR/x86_64-apple-ios/release/$LIB_NAME" \
    -output "$SIM_LIB_DIR/$LIB_NAME"

echo "==> Regenerating Swift bindings (headers included)"
rm -rf "$SWIFT_OUT_DIR"
mkdir -p "$SWIFT_OUT_DIR"
(
    cd "$FFI_CRATE_DIR"
    cargo run -p macula-rust-sdk-ffi --release --bin uniffi-bindgen -- generate \
        --library "$TARGET_DIR/aarch64-apple-ios/release/$LIB_NAME" \
        --language swift \
        --out-dir "$SWIFT_OUT_DIR" \
        --no-format
)
# uniffi-bindgen's Swift output splits the C header/modulemap from the
# .swift source; the XCFramework wants the headers in their own dir, the
# .swift file compiled directly into the app target.
HEADERS_DIR="$SWIFT_OUT_DIR/headers"
mkdir -p "$HEADERS_DIR"
mv "$SWIFT_OUT_DIR"/*.h "$HEADERS_DIR/"
mv "$SWIFT_OUT_DIR"/*.modulemap "$HEADERS_DIR/module.modulemap"

echo "==> Assembling XCFramework"
rm -rf "$XCFRAMEWORK_OUT"
xcodebuild -create-xcframework \
    -library "$TARGET_DIR/aarch64-apple-ios/release/$LIB_NAME" -headers "$HEADERS_DIR" \
    -library "$SIM_LIB_DIR/$LIB_NAME" -headers "$HEADERS_DIR" \
    -output "$XCFRAMEWORK_OUT"

echo "==> Done. XCFramework: $XCFRAMEWORK_OUT"
echo "==> Done. Swift bindings: $SWIFT_OUT_DIR/*.swift"
