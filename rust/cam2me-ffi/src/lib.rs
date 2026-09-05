//! This crate exists only to be the top-level `cargo`/`cargo-ndk` build
//! target for [`macula_rust_ffi`], now that it's consumed as a normal
//! `crates.io` dependency (see `Cargo.toml`) rather than a git submodule
//! built directly.
//!
//! An otherwise-empty pass-through crate silently drops its dependency
//! entirely at link time: rustc/the linker treats an unreferenced
//! dependency as dead code and excludes its whole compiled unit, not
//! just individually-unused symbols within it — `#[no_mangle] extern
//! "C"` alone does not save it, since the crate containing those
//! functions never gets pulled into the link at all. Verified (before
//! writing this) against a scratch shell crate: an empty one produced a
//! 403KB `.so` with zero defined dynamic symbols, versus 11MB / 237
//! symbols building `macula-rust-ffi` directly. The `pub use` line below
//! is what forces rustc to treat the dependency as referenced — rebuilt
//! after adding it, `.so` size and symbol count matched the direct build
//! exactly (diffed the full sorted symbol list, byte-for-byte identical),
//! and `uniffi-bindgen` (both Kotlin and Swift) generates the complete
//! API surface through this crate's own compiled library, not a
//! truncated one.
pub use macula_rust_ffi as _reexport;
