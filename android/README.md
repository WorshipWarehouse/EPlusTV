# GameDay — native Android TV application

A standalone television app for authorised live sports streams: its own program guide,
configuration, cache, signed updates and Media3 player. Runs on Fire TV, Android TV and
LineageOS ATV. No Docker, Termux, root, emulator or external server at runtime.

```
android/
├── app/                     Gradle module (com.gameday.tv)
│   ├── src/main/java/com/gameday/tv/
│   │   ├── core/            HTTP transport, sanitized diagnostics, formatting, key/value store
│   │   ├── model/           Channel, Program, GuideSnapshot, SourceConfig
│   │   ├── epg/             M3U + XMLTV parsers, queries, pruner, atomic cache, refresh repository
│   │   ├── providers/       source store (encrypted headers), favorites, header parsing
│   │   ├── storage/         settings + schema migration, Keystore-backed secret store
│   │   ├── update/          manifest validation, APK verification, downloader, installer
│   │   ├── playback/        Media3 player, retry policy, error messages, intent contract
│   │   ├── refresh/         background refresh alarm + receiver
│   │   ├── ui/              single-activity TV shell, screens, EPG grid, on-screen keyboard
│   │   ├── AppContainer.kt  in-process "backend" wiring
│   │   └── GameDayApp.kt
│   └── src/test/java/…      64 JVM unit tests
├── docs/                    audit, architecture decision, compatibility, install, Phase 2 record
├── scripts/                 build, install (ADB), verify APK, update manifest, device benchmark
├── NOTICE.md                third-party attribution (EPlusTV is MIT)
└── gradle/                  wrapper (Gradle 8.7)
```

## Status, stated precisely

Verified: compiles; **64 unit tests pass**; **Lint 0 errors** (`minSdk 25` API check clean);
debug and release APKs assemble; release APK signature verifies (v2 scheme).

Not verified: nothing has been executed on Android hardware or an emulator — the build host
has no KVM and no device. See `docs/PHASE2_RUNTIME_POC.md` for the exact gap and the script
that measures real devices.

## Architecture in one paragraph

Everything runs in one process. `AppContainer` wires a blocking HTTP transport, a
`SourceStore` (JSON list in SharedPreferences + headers sealed by Android Keystore AES-GCM),
a bounded guide cache file, and `GuideRepository`, which fetches every enabled source
(`M3U`, `EPlusTV`, `DIRECT`), parses M3U/XMLTV, prunes to ≤ 1,000 channels / ≤ 20,000
programmes, and writes the snapshot atomically. The UI reads the snapshot synchronously.
Playback hands a channel's manifest URL and headers to a Media3 `ExoPlayer` with the HLS
module; nothing is proxied or transcoded. Refreshes are triggered by a single-flight
repository call from the UI, and by an inexact `AlarmManager` alarm plus a staleness check on
start/resume. Updates are signed-APK only.

## Build and check

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./scripts/build.sh assembleDebug      # or assembleRelease
./scripts/verify-apk.sh app/build/outputs/apk/release/app-release.apk
```

Install and operate: see [`docs/INSTALL.md`](docs/INSTALL.md).

## Why Kotlin instead of embedding EPlusTV's Node.js

See [`docs/ARCHITECTURE_DECISION.md`](docs/ARCHITECTURE_DECISION.md). In short: Media3 makes
HLS playback native, nodejs-mobile cannot run `yt-dlp`/`process.exit`, `sharp` would need NDK
cross-compilation, armeabi-v7a is marked discontinued there, and the provider authentication
layer cannot be shipped under the project's own security rules. The upstream EPlusTV server
stays in this repository, untouched, and can be used as a LAN source.

## Security

- Secrets: AES-256-GCM with a non-exportable Android Keystore key; fail-closed if unavailable.
- Logs: `Diag` redacts tokens, cookies, bearer/JWT values and URL credentials before storing.
- Updates: HTTPS-only manifest, SHA-256, same-signing-key and version checks, system installer
  confirmation, old APK retained if verification fails.
- Permissions: INTERNET, ACCESS_NETWORK_STATE, REQUEST_INSTALL_PACKAGES. `allowBackup=false`.
- Cleartext HTTP is permitted only because LAN EPlusTV gateways speak HTTP; update traffic is
  forced to HTTPS in code.

## Provider restrictions

GameDay does not sign in to providers, does not spoof device identities, and does not bypass
DRM. DRM-protected streams will not play; the player says so explicitly. Use supported
provider apps or your own authorised endpoints.

## Licence

The application code is provided under the repository's MIT licence (`../LICENSE`).
EPlusTV attribution: `NOTICE.md`.
