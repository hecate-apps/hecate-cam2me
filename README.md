# hecate-cam2me

Native Android + iOS apps that stream a phone's camera over the [macula
mesh](https://github.com/macula-io/macula-rust-sdk) — pull-model: other
mesh participants dial into the phone via a macula-station it's
connected to, not the other way around. Skeleton stage right now; no
camera capture or mesh session wired up yet.

## Architecture

Two separate native apps, sharing nothing but the Rust core:

```
hecate-cam2me/
├── rust/macula-rust-sdk/     # git submodule -> macula-io/macula-rust-sdk
├── android/                  # Kotlin, Gradle, generated bindings via JNA
├── ios/                      # Swift, XcodeGen, generated bindings via XCFramework
└── scripts/                  # cross-compile the Rust core + regenerate bindings
```

No Flutter, no Kotlin Multiplatform. Both `macula-rust-sdk-ffi`'s Kotlin
and Swift bindings already exist and are already verified (generated,
inspected, and CI-checked in `macula-rust-sdk` itself); this repo just
consumes them, once per platform, the same way `iroh-ffi`'s own example
apps consume `iroh`.

Neither app links against a prebuilt binary. `scripts/build-rust-*.sh`
cross-compile `macula-rust-sdk-ffi` from the pinned submodule commit and
regenerate the Kotlin/Swift source fresh every time — nothing FFI-shaped
is committed to this repo, matching `macula-rust-sdk`'s own "don't
commit generated code" convention.

## Building

### Android

Requires: Android NDK (`ANDROID_NDK_HOME` set), `cargo-ndk`
(`cargo install cargo-ndk`), and the Android Rust targets:

```sh
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
./scripts/build-rust-android.sh
cd android && ./gradlew build   # or: gradle wrapper --gradle-version 9.1 first,
                                 # the wrapper jar itself isn't committed
```

Or just open `android/` in Android Studio after running the build
script once — it'll offer to generate the Gradle wrapper itself.

### iOS

Requires: macOS with Xcode, [XcodeGen](https://github.com/yonaskolb/XcodeGen)
(`brew install xcodegen`), and the iOS Rust targets:

```sh
rustup target add aarch64-apple-ios aarch64-apple-ios-sim x86_64-apple-ios
./scripts/build-rust-ios.sh
cd ios && xcodegen generate
open HecateCam2Me.xcodeproj
```

**Honest status note:** this repo was scaffolded from a Linux
environment with no Android SDK/NDK and no macOS/Xcode available
locally. The Android side follows a well-established, standard Gradle
shape; the iOS side is built through XcodeGen specifically *because*
hand-authoring a raw `.xcodeproj` without ever being able to open it in
Xcode is too fragile to trust — XcodeGen's `project.yml` is a much
smaller, well-documented surface to get right blind. Both are verified
by CI (`.github/workflows/android.yml` on `ubuntu-latest`,
`ios.yml` on `macos-latest`, which has real Xcode) rather than by local
testing. Check the Actions tab before assuming either one actually
builds.

## Current status

Both apps do exactly one thing: generate a puzzle-hardened Ed25519
identity via `FfiKeyPair.generate()` and display its `node_id`. No
network connection, no camera, no mesh session yet — this proves the FFI
wiring end to end on both platforms without pulling in any feature work.

Everything downstream of that — `FfiSession.connect`, `advertise`,
`acceptStream`, camera capture, actually pushing frames — is a
deliberately separate next pass.

## See also

- [macula-io/macula-rust-sdk](https://github.com/macula-io/macula-rust-sdk) —
  the Rust core + UniFFI bindings this repo consumes. Its
  `plans/PLAN_WIRE_PROTOCOL.md` §13 covers the streaming RPC provider
  role this app will use to answer inbound viewers.
