# Third-Party Software & Licenses — WiFi Audio Streaming (Android)

Copyright (c) 2025-2026 Marco Morosi and contributors. The app is licensed
under the **EUPL v1.2** (see `LICENSE.md`), except for the two sound files
described below.

This page summarises the licences of what the APK contains. The complete,
authoritative list — with the full text of the licences and the NOTICE files
they require — is
**[`app/src/main/res/raw/third_party_licenses.txt`](app/src/main/res/raw/third_party_licenses.txt)**,
which the app shows in Settings → *Open-source licenses*.

## Not under the EUPL

| File | Origin | Terms |
|---|---|---|
| `app/src/main/res/raw/connection_sound.wav`, `app/src/main/res/raw/disconnection_sound.wav` | Free sounds | No restriction: they can be used, changed and redistributed, also commercially, with no attribution required. |

## Credits for the app's own content

- **WFAS v2 protocol**: the C reference implementation is published separately
  under the MIT License ([`wfas-protocol`](https://github.com/marcomorosi06/wfas-protocol),
  © 2026 Marco Morosi). It is not bundled in this app.
- **WFAS v3 protocol**: the specification and the C reference core are in
  [`app/src/main/cpp/wfas3/core`](app/src/main/cpp/wfas3/core), under the MIT
  License (© 2026 Marco Morosi), and are compiled into the app (`libwfas3.so`).

## What the APK contains

| Component | Licence |
|---|---|
| AndroidX and Jetpack Compose (activity, appcompat, camera 1.4.2, compose, core, datastore, glance, lifecycle, room, security-crypto, sqlite, work and the others listed in the notices), with their native libraries | Apache-2.0 |
| Kotlin standard library and kotlin-reflect, kotlinx.coroutines 1.8.1, kotlinx.serialization | Apache-2.0 (Kotlin's stdlib also contains Boost-licensed code) |
| Ktor 2.3.11 (network, io, utils) | Apache-2.0 |
| ZXing Core 3.5.3, zxing-cpp 2.3.0 (with `libzxingcpp_android.so`) | Apache-2.0 |
| Tink (used by AndroidX security-crypto), Gson 2.8.9, Guava ListenableFuture, AutoValue annotations, JSpecify, JetBrains annotations | Apache-2.0 |
| `libwfas3.so` (WFAS v3): the WFAS v3 reference core, HACL* (hacl-packages) with the KaRaMeL headers, libcrux ML-KEM-768, fiat-crypto (Curve25519 field arithmetic), libopus 1.5.2; source in [`app/src/main/cpp/wfas3`](app/src/main/cpp/wfas3), each file pinned by SHA-256 | MIT (WFAS v3 core, HACL*, libcrux, fiat-crypto used under MIT), Apache-2.0 (KaRaMeL headers), BSD-3-Clause (libopus, with royalty-free patent licences) |
| Bouncy Castle 1.86 (`bcprov-jdk18on`) | Bouncy Castle Licence (MIT) |
| SLF4J API 1.7 | MIT |

AAC encoding uses Android's own `MediaCodec` API: unlike the desktop app, the
APK bundles no FFmpeg and no other third-party media codec. It contains no
proprietary dependency and no Google Play Services or ML Kit component.
