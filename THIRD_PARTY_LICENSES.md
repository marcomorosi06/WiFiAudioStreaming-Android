# Third-Party Software & Licenses — WiFi Audio Streaming (Android)

This application is licensed under the **EUPL v1.2** (see `LICENSE.md`).
It uses the third-party open-source components listed below. Each remains the
property of its respective authors and is distributed under its own licence.
We are grateful to all of these projects.

> **WFAS v2 protocol (MIT):** the WiFi Audio Streaming wire protocol is this
> project's own. A C reference implementation is published separately under the
> **MIT License** (Copyright © 2026 Marco Morosi,
> <https://github.com/marcomorosi06/wfas-protocol>) so anyone can adopt WFAS v2 freely,
> e.g. on microcontrollers. It is not bundled in this app.

---

## Summary table

| Component | Version | Licence | Copyright |
|---|---|---|---|
| AndroidX Core KTX, Activity, Activity Compose | (per BOM / pinned) | Apache License 2.0 | The Android Open Source Project / Google LLC |
| AndroidX Lifecycle (runtime-ktx, viewmodel-compose) | 2.8.x | Apache License 2.0 | The Android Open Source Project / Google LLC |
| Jetpack Compose (UI, Graphics, Tooling, Material 3) | via Compose BOM | Apache License 2.0 | The Android Open Source Project / Google LLC |
| Compose Material Icons Extended | 1.5.0 | Apache License 2.0 | The Android Open Source Project / Google LLC |
| AndroidX DataStore Preferences | 1.0.0 | Apache License 2.0 | The Android Open Source Project / Google LLC |
| AndroidX Glance (App Widget, Material 3) | 1.1.0 | Apache License 2.0 | The Android Open Source Project / Google LLC |
| AndroidX Core SplashScreen | 1.0.1 | Apache License 2.0 | The Android Open Source Project / Google LLC |
| Kotlin standard library | (Kotlin plugin) | Apache License 2.0 | JetBrains s.r.o. |
| kotlinx.coroutines (Android) | 1.8.0 | Apache License 2.0 | JetBrains s.r.o. |
| Ktor (client-core, client-cio, server-core, server-cio, network) | 2.3.11 | Apache License 2.0 | JetBrains s.r.o. |
| Bouncy Castle (`bcprov`, `bctls`, `bcpkix` jdk15on) | 1.70 | Bouncy Castle Licence (MIT-style) | The Legion of the Bouncy Castle Inc. |
| JUnit 4 *(test only, not shipped)* | — | Eclipse Public License 1.0 | JUnit contributors |
| AndroidX Test, Espresso *(test only, not shipped)* | — | Apache License 2.0 | The Android Open Source Project |

Versions reflect `app/build.gradle.kts` and the version catalog at the time of
writing; transitive dependencies inherit the licence of their project.

---

## Apache License 2.0

The following components are licensed under the Apache License, Version 2.0
(<https://www.apache.org/licenses/LICENSE-2.0>):

* AndroidX libraries and Jetpack Compose — © The Android Open Source Project / Google LLC
* Kotlin standard library and kotlinx.coroutines — © JetBrains s.r.o.
* Ktor — © JetBrains s.r.o.

A full copy of the Apache License 2.0 is available at the URL above. `NOTICE`
files shipped by these projects are preserved in their respective artifacts.

---

## Bouncy Castle Licence

Bouncy Castle (`bcprov-jdk15on`, `bctls-jdk15on`, `bcpkix-jdk15on` 1.70) is
© 2000–2021 The Legion of the Bouncy Castle Inc. (<https://www.bouncycastle.org>)
and is distributed under an MIT-style licence:

```
Copyright (c) 2000–2021 The Legion of the Bouncy Castle Inc. (https://www.bouncycastle.org)

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. ...
```

---

## How to regenerate this list

The authoritative source is `app/build.gradle.kts` and `gradle/libs.versions.toml`.
To dump the full resolved dependency tree run:

```
./gradlew app:dependencies --configuration releaseRuntimeClasspath
```
