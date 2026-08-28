plugins {
    id("com.android.application") version "9.3.0" apply false
    // Separate from AGP's built-in Kotlin support (which only replaces
    // org.jetbrains.kotlin.android) -- Compose's own compiler
    // transformations need this applied explicitly, and its version
    // tracks the Kotlin language version 1:1. AGP 9.0+ bundles Kotlin
    // 2.2.10, so this must match that exactly.
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
