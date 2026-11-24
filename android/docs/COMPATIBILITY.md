# GameDay compatibility and validation report

Build: `com.gameday.tv` 1.0.0 (versionCode 1) · compileSdk 34 · **minSdk 25** · targetSdk 34
Artifacts: `app-release.apk` 5,092,947 bytes · `app-debug.apk` 6,663,593 bytes

## 1. What was validated, and how

| Check | Result |
| --- | --- |
| Kotlin/Java compile (`:app:compileDebugKotlin`, `:app:compileReleaseKotlin`) | Pass |
| Unit test suite (`:app:testDebugUnitTest`) | **62 tests, 0 failures** |
| Android Lint (`:app:lintDebug`, `abortOnError = true`) | **0 errors, 36 warnings** (no `NewApi` findings) |
| Debug APK assembly | Pass |
| Release APK assembly + signature verification (`apksigner verify`) | Pass, APK Signature Scheme **v2**, 1 signer |
| Manifest assertions (`aapt2 dump badging/permissions`) | minSdk 25, targetSdk 34, 3 app permissions |
| Launch on hardware / emulator | **Not performed** — see §2 |

`NewApi` being clean means no API-level incompatibility was found for `minSdk 25`; that is a
static check, not a runtime proof.

## 2. Why no on-device run is reported

The build environment has no Android emulator (`/dev/kvm` is not accessible to the build
user) and no attached armv7/arm64 device, so nothing was installed or executed on Android.
Everything below labelled "implemented" is code plus unit tests; nothing is claimed to be
observed running on a TV.

To validate on hardware, see `PHASE2_RUNTIME_POC.md` and `scripts/benchmark-device.sh`.

## 3. Target matrix

| Device | Platform | ABI | Status |
| --- | --- | --- | --- |
| Fire TV Stick 4K (1st gen) | Fire OS 6 / API 25 | armeabi-v7a | **Build target.** APK installs (`minSdk 25`); runtime not validated here |
| Raspberry Pi 4 (8 GB), LineageOS 24 ATV | API 34-ish | arm64-v8a | APK is ABI-neutral (no native libs); not validated here |
| Modern Fire TV / Google TV | API 28+ | arm64-v8a | Expected to run; not validated |
| macOS | — | — | Out of scope |

The APK contains **no native libraries**, so ABI never blocks installation. `abiFilters`
(`armeabi-v7a`, `arm64-v8a`) is declared so any future native dependency stays within the two
target ABIs.

## 4. Feature status

| Feature | Status | Evidence |
| --- | --- | --- |
| M3U channel parsing (attrs, `#EXTGRP`, `EXTVLCOPT`, inline headers) | Implemented | `ParserTest` (4 tests) |
| XMLTV parsing, offsets, channel filtering, malformed input | Implemented | `ParserTest` (4 tests) |
| Guide refresh across sources, merge, bounded prune | Implemented | `GuideRepositoryTest`, `GuideQueryTest` |
| Cache write/read, corruption recovery, atomic replace | Implemented | `GuideCacheTest` |
| Now/next, upcoming, search, favorites queries | Implemented | `GuideQueryTest` |
| Settings persistence + schema migration | Implemented | `SettingsTest` |
| Encrypted source headers (Keystore AES/GCM) | Implemented (unit-tested storage logic; Keystore itself is platform code) | `SourceStoreTest`, `SecretStore` |
| Signed update verification (SHA-256, signer, version, API) | Implemented | `UpdateVerifierTest` (8 tests) |
| Playback failure messages + retry policy | Implemented | `PlaybackRetryTest` |
| HLS playback, audio/subtitle track selection, aspect, buffering, error overlay | Implemented in `PlayerActivity` | Not device-validated |
| TV UI: Home, Live, Guide, Favorites, Search, Sources, Settings, Diagnostics, Updates | Implemented | Not device-validated |
| Background refresh | Implemented (inexact alarm + staleness on start/resume) | Not device-validated |
| Provider account logins (Fox, NFL, MLB, …) | **Not implemented, by design** | `UPSTREAM_AUDIT.md` §5, `ARCHITECTURE_DECISION.md` §5 |
| DRM-protected provider streams | **Not supported** | See §5 |
| Recording / timeshift / casting / recommendations | Not implemented (out of scope) | — |

## 5. Provider and DRM restrictions (explicit)

- GameDay plays **HLS** from addresses the user already has access to. It never asks for
  provider passwords and cannot sign in to Fox/ESPN/NFL/MLB/etc. itself.
- **Widevine / DRM content is not playable.** No license server is configured, and no DRM
  bypass exists in the code by design. If a provider's manifest requires DRM, playback stops
  with a message saying GameDay does not bypass DRM. Use the provider's own app/device.
- Cleartext HTTP is enabled in the manifest so an EPlusTV gateway on the LAN
  (`http://192.168.x.x:8000`) works. Update manifests are forced to HTTPS in code, and update
  APKs are only installed after checksum + signing-certificate verification.

## 6. Playback stack

AndroidX Media3 **1.3.1** (`media3-exoplayer`, `media3-exoplayer-hls`, `media3-ui`):
minSdk 21, HLS module included, no separate OkHttp dependency (uses
`DefaultHttpDataSource`). Player configuration:

- `DefaultRenderersFactory` with decoder fallback; optional software-only selector.
- `DefaultLoadControl` 15 s / 45 s buffer, 1.5 s playback / 3 s rebuffer thresholds.
- `HlsMediaSource` with chunkless preparation and cross-protocol redirects.
- Track preferences: preferred audio language, text tracks on/off, data-saver bitrate cap.
- Buffering indicator via `PlayerView`, retry with exponential backoff, explicit messages.

## 7. Security posture

| Control | Implementation |
| --- | --- |
| Credential storage | AES-256-GCM key in Android Keystore; ciphertext in private prefs; fail-closed |
| Logs | `Diag` redacts token/password/cookie/bearer/JWT/user-info/long-base64 before retaining |
| Backups | `android:allowBackup="false"` |
| Permissions | `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES` only |
| Update integrity | HTTPS manifest, SHA-256, same-signing-key check, version check, system installer confirmation |
| Network | No inbound sockets; no local server; direct HTTPS/TLS to sources |
| Cleartext | Allowed only because LAN gateways are HTTP; documented above |

## 8. Known limitations

1. Search matches titles and channel names only (not descriptions or cast).
2. Guide window is 4 hours per screen, refreshed by moving through programmes.
3. No picture-in-picture, no background audio.
4. Playback starts at the live edge; no DVR seek beyond what the provider manifest offers.
5. Release builds are **debug-key signed** unless `android/keystore.properties` is provided;
   `versionCode` must be incremented for an update to be accepted.
6. R8/minification is off (see `app/build.gradle.kts`) — a deliberate choice until release
   behaviour can be validated on hardware.
7. Lint warnings retained: hardcoded UI strings (no localisation yet), unused resources,
   `notifyDataSetChanged` (small lists), `VectorRaster` for the banner, `DataExtractionRules`.

## 9. Performance

No physical-hardware measurements exist yet. The design targets the 1.5 GB Fire TV Stick:

- No background process, no server, no WebView, no native runtime.
- Guide is pruned to ≤ 1,000 channels / ≤ 20,000 programmes and written atomically.
- Guide views are recycled; only visible guide rows are inflated.
- Segments are decoded straight from the CDN by ExoPlayer (no proxy, no double buffer).
- Playback is released in `onDestroy`; the player pauses in `onStop`.

`scripts/benchmark-device.sh` captures cold start, memory and playback stats on real
hardware and writes them to `docs/PERFORMANCE_DEVICE.md`.
