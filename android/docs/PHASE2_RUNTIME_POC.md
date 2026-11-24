# Phase 2 — embedded runtime proof of concept: record

This document exists to be unambiguous about what was and was not demonstrated.

## 1. Outcome

The Phase 2 gate ("do not proceed until the embedded runtime is demonstrated to work") was
**not satisfied for an embedded Node.js runtime**, because the architecture decision rejected
that runtime for this target (see `ARCHITECTURE_DECISION.md`). What was built and verified
instead is a native APK with an in-process backend that has no runtime to embed.

**No part of this work was executed on Android hardware or an emulator.** The build host has
no accessible KVM device and no attached device, so ARMv7/ARM64 execution, on-device memory
and API 25 runtime behaviour remain **unvalidated**.

## 2. Phase 2 checklist

The brief's checklist is reproduced with the actual status for (a) the embedded-Node idea and
(b) the shipped native design.

| Check | Embedded Node PoC | Native app (shipped) |
| --- | --- | --- |
| Runtime startup | Not built. NodeMobile 18.20.4 would start a Hono server on loopback | No separate runtime to start; `Application.onCreate` builds the container |
| ARMv7 execution | Not validated (no device/emulator); NodeMobile ships armeabi-v7a but marks it discontinued | Not validated on device; no native code, so nothing ABI-specific |
| ARM64 execution | Not validated | Not validated; no native code |
| API 25 compatibility | NodeMobile requires API ≥ 24 | `minSdk 25`, Lint `NewApi` clean, not executed |
| Persistent app storage | Would use `config/` via NeDB | `SharedPreferences` + files dir; cache logic unit-tested |
| HTTPS networking | Would use Node `https` | `HttpURLConnection` + Media3 HTTP data source; failure handling unit-tested with fakes |
| Local backend communication | Required loopback HTTP + JSON | Not required — direct method calls |
| JSON parsing | `JSON.parse` | kotlinx.serialization; round-trip and corruption cases unit-tested |
| Backend shutdown | `process.exit` unsupported on mobile | Player released in `onDestroy`; no process to stop |
| Crash recovery | Would need a supervisor | Corrupt cache discarded; failed refresh keeps last guide; failed update keeps old APK |
| CPU/memory consumption | Not measured | Not measured on device; see `COMPATIBILITY.md` §9 |

## 3. What *was* measured (build host)

| Metric | Value |
| --- | --- |
| Unit tests | 62 executed, 62 passed, 0 skipped |
| Lint | 0 errors, 36 warnings, no `NewApi` |
| Release APK | 5,092,947 bytes (4.9 MiB), APK Signature Scheme v2 |
| Debug APK | 6,663,593 bytes |
| DEX references | 84,164 across 2 dex files |
| Native libraries in APK | none |
| Dependencies | 6 direct runtime libraries (core-ktx, appcompat, recyclerview, lifecycle-runtime-ktx, coroutines, serialization + 3 Media3 artifacts) |

These are desktop build facts. They say nothing about frame rates or heap on a Fire TV Stick.

## 4. Why NodeMobile was not pursued further

1. `child_process.spawn` is unavailable on nodejs-mobile, so EPlusTV's `yt-dlp` providers can
   never work.
2. `process.exit()` is unsupported, so the server's signal handler would have to be patched.
3. `sharp` would need libvips cross-compiled with the NDK against the NodeMobile ABI.
4. armeabi-v7a is marked discontinued in nodejs-mobile's Android guide.
5. Even if all of that succeeded, the provider authentication layer (embedded client secrets,
   generated device IDs, vendor UA spoofing) cannot be shipped under the mission's own
   restrictions, so the runtime would only host guide parsing that Kotlin does in-process.

## 5. How to close the gap on hardware

On a Fire TV Stick (ADB enabled) or a Pi 4 running LineageOS ATV:

```bash
cd android
./scripts/build.sh assembleDebug
ADB="adb" ./scripts/install.sh
./scripts/benchmark-device.sh          # cold start, memory, playback stats
adb logcat -s GameDay:V AndroidRuntime:E | tee /tmp/gameday-runtime.log
```

Success criteria for the still-open Phase 2 items:

- App launches from the TV launcher and renders Home within a few seconds.
- Adding an M3U source and refreshing produces channels; the guide shows now/next.
- A known-good HLS test stream plays with hardware decoding (check `dumpsys media.codec`).
- `dumpsys meminfo com.gameday.tv` stays well under ~250 MB during guide + playback.
- Killing the process during refresh leaves a usable guide on next start (cache atomicity).
- Update flow: publish manifest with an incremented `versionCode` and confirm the installer
  prompts and rejects a tampered APK.

Record results in `docs/PERFORMANCE_DEVICE.md` (created by the benchmark script).
