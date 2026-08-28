plugins {
    id("com.android.application") version "9.3.0" apply false
    // Room's KSP annotation processor needs an *exact* Kotlin version
    // match -- unlike most dependencies, KSP is tied to the Kotlin
    // compiler's internals. AGP 9's "built-in Kotlin" support is barely
    // a month old with no documented way to read back its exact bundled
    // version, so guessing a matching KSP release against it is too
    // fragile to build on. Opting out (android.builtInKotlin=false in
    // gradle.properties) and applying kotlin-android explicitly instead
    // -- the well-trodden path where the Kotlin version is a plain fact
    // I set myself, and the matching KSP release is directly documented.
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    // Compose's compiler transformations need this applied explicitly
    // regardless of which Kotlin plugin is in use; its version tracks
    // the Kotlin language version 1:1, so it moves with kotlin-android
    // above.
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    // Version format is <kotlin-version>-<ksp-version>; confirmed
    // 2.2.21-2.0.5 is a real, published release pairing, not guessed.
    id("com.google.devtools.ksp") version "2.2.21-2.0.5" apply false
}
