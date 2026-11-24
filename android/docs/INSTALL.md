# GameDay installation and operation

## 1. Build from source

Prerequisites:

- JDK **17**
- Android SDK with `platforms;android-34` and `build-tools;34.0.0`
- No Docker, Termux, root or server is needed at build or run time.

```bash
cd android
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./scripts/build.sh                # debug APK (assembleDebug)
./scripts/build.sh assembleRelease
```

Artifacts:

- `app/build/outputs/apk/debug/app-debug.apk` (debug, app id `com.gameday.tv.debug`)
- `app/build/outputs/apk/release/app-release.apk` (release)

Checks:

```bash
./gradlew :app:testDebugUnitTest    # 62 unit tests
./gradlew :app:lintDebug            # API-level and correctness lint
./scripts/verify-apk.sh app/build/outputs/apk/release/app-release.apk
```

### Release signing

Without configuration, release builds are signed with the local debug key so an installable
APK always exists. For a real release, create `android/keystore.properties` (git-ignored):

```properties
storeFile=/absolute/path/gameday-release.jks
storePassword=…
keyAlias=gameday
keyPassword=…
```

Generate the key once with:

```bash
keytool -genkeypair -v -keystore ~/gameday-release.jks -alias gameday \
  -keyalg RSA -keysize 2048 -validity 10000
```

Keep the keystore and passwords out of the repository. Losing the key means existing installs
cannot be updated, only reinstalled.

## 2. Install on a Fire TV Stick (4K, 1st gen)

1. On the Fire TV: Settings → My Fire TV → Developer Options → enable **ADB debugging**.
2. Note the device IP: Settings → My Fire TV → About → Network.
3. From a computer on the same network:

```bash
adb connect 192.168.1.50:5555
cd android
ADB="adb" ./scripts/install.sh
```

The app appears as **GameDay** in the launcher. Sideloading is expected; the app is not in the
Amazon store.

For self-updates through the app, Android requires the "install unknown apps" grant:
Settings → My Fire TV → Developer Options → **Install unknown apps** → allow GameDay. The
Updates screen can open the exact settings page.

## 3. Install on Raspberry Pi 4 / LineageOS 24 ATV

LineageOS ATV provides ADB over the network the same way:

```bash
adb connect 192.168.1.60:5555
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.gameday.tv/com.gameday.tv.ui.MainActivity
```

The APK has no native libraries, so `arm64-v8a` is served by the same file as `armeabi-v7a`.

## 4. First run: add a source

GameDay plays streams you are entitled to. Sources → **Add source**:

| Type | What to enter | Guide |
| --- | --- | --- |
| **M3U playlist** | URL of an M3U/M3U8 playlist you are authorised to use | Optional XMLTV URL (set later via “Edit URL”, or provide a playlist with `tvg-id`) |
| **EPlusTV gateway** | `http://<your-eplustv-host>:8000` | Taken automatically from `/channels.m3u` and `/xmltv.xml` |
| **Single HLS channel** | One HLS manifest URL | None |

Per-source HTTP headers (for example `Authorization=Bearer …`) are entered as
`Name=Value; Name=Value` and stored encrypted with an Android Keystore key. Rename, disable,
re-point or delete a source from the same screen.

Text is entered with an on-screen keyboard operated by the remote; no phone, mouse or app is
required.

## 5. Remote controls

| Key | Screen | Action |
| --- | --- | --- |
| D-pad | everywhere | move focus (focus is shown by an outline and slight scale) |
| OK | lists/cards | open, play, toggle |
| OK (hold) | Live TV / Favorites / Guide | add or remove a favorite |
| Back | content | focus the sidebar |
| Back | sidebar | go to Home |
| Back | Home | exit |
| Menu or Info | player | audio tracks, subtitles, aspect ratio, playback info, retry |
| Play/Pause, FF/RW | player | ExoPlayer transport controls |

## 6. Updates

1. Build and sign a new APK with a **higher `versionCode`**.
2. Publish the APK over HTTPS.
3. Generate the manifest:

```bash
./scripts/make-update-manifest.sh app/build/outputs/apk/release/app-release.apk \
  https://downloads.example.com/gameday/app-release.apk
```

4. Host the JSON at an HTTPS URL and set it in Settings → **Update manifest URL** (or in the
   Updates screen).
5. Updates → **Check for updates** → **Download and install**.

The client rejects: non-HTTPS manifests, bad SHA-256, a different signing key, a version that
does not match the manifest, and updates requiring a newer API level. Installation always goes
through the system installer, which shows its own confirmation. Configuration, sources,
favorites and encrypted headers survive an update.

Example manifest: `docs/update-manifest.example.json`.

## 7. Diagnostics

Diagnostics shows app build, API level, ABI, heap use, guide size, cache size, source list
(with URLs redacted of credentials), last refresh result, and the sanitised log ring buffer.
Buttons: refresh guide, clear guide cache, clear log. Nothing is uploaded anywhere.

## 8. Troubleshooting

| Symptom | Likely cause / fix |
| --- | --- |
| “No sources configured” | Add a source on the Sources screen |
| 401/403 playback error | Provider token expired or headers missing; update the source headers |
| “Not supported on this device” | Manifest/codec unsupported; try another variant or provider app |
| “Could not decode” | Settings → turn off “Prefer hardware decoding” |
| “GameDay does not bypass DRM” | The stream is DRM-protected; use the provider's own app/device |
| Guide empty but channels play | No XMLTV URL for that source, or the EPG has no matching `tvg-id` |
| Update will not install | Enable “Install unknown apps” for GameDay, and check `versionCode` increased |
| Guide error remains after a network fix | Diagnostics → Refresh guide; the old cache is kept until a refresh succeeds |

## 9. Uninstall

`adb uninstall com.gameday.tv` (release) or `com.gameday.tv.debug`. This removes the guide
cache, settings and encrypted headers. Uninstalling does not affect any EPlusTV server you
run elsewhere.
