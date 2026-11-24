# Architecture decision: native Kotlin, not embedded Node.js

**Status:** accepted · **Date:** 2026-11-24 · **Supersedes:** the mission's Phase 1
"evaluate embedding Node.js or porting to Kotlin" question.

## 1. Decision

GameDay is a single native Android/Kotlin application. The guide, configuration, caching,
update verification and playback are implemented in-process. **No Node.js runtime, HTTP
server, WebView, Docker container, Termux environment or external server is used at
runtime.** The EPlusTV server remains in the repository, untouched, as a compatible LAN
gateway that GameDay can consume as a source.

## 2. Options considered

| Option | What it means | Fatal/limiting issues for this target |
| --- | --- | --- |
| **A. Embed Node.js (nodejs-mobile) and run EPlusTV as-is** | Ship NodeMobile `.so`, `nodejs-project` assets, keep Hono server on `127.0.0.1`, build a TV UI in Kotlin/WebView talking to it | native addon cross-compile (`sharp`), `yt-dlp` impossible, `process.exit` unsupported, ~2× memory, deprecated armeabi-v7a, two runtimes to keep alive/recover, provider secrets and device spoofing inside the APK |
| **B. Port the compatible backend to Kotlin (chosen)** | Parse M3U/XMLTV in Kotlin, cache guide, verify signed APKs, play HLS with Media3 | loses EPlusTV's provider logins (deliberately, see §5); everything else is straightforward |
| **C. Hybrid: Kotlin UI, Node only for provider scraping** | Keep NodeMobile for the hard providers | keeps every problem in A while adding IPC; no authorised provider path exists to justify it |

## 3. Evidence for rejecting A

Verified from upstream sources and nodejs-mobile's own documentation/releases:

1. **Node version matches, ABI does not.** nodejs-mobile's latest release is
   **v18.20.4 (2024-10-07)**, Node 18 — matching `.nvmrc`. Its release does include
   `armeabi-v7a`, but the official Android guide labels that ABI **"(discontinued)"**, so
   the primary target (Fire TV Stick 4K 1st gen) sits on the least-supported path of an
   already slow-moving runtime.
2. **External executables are not possible.** `services/yt-dlp-helper.ts` shells out to
   `yt-dlp`. nodejs-mobile's FAQ states APIs that create processes hit permission issues on
   mobile. Every YouTube-backed provider would be dead code in the APK.
3. **Shutdown path is not portable.** `index.tsx` calls `process.exit(0)` on `SIGTERM`.
   nodejs-mobile documents `process.exit()` as not allowed.
4. **Native dependency.** `sharp` requires a `.node` binary built against the NodeMobile ABI
   (modules 108) with libvips cross-compiled for `armeabi-v7a` via the NDK. That is a
   build-system project in its own right, for thumbnails the TV UI does not need.
5. **Memory.** Target budget: 1.5 GB RAM shared with Fire OS. Node 18 baseline + jsdom DOMs
   (`runScripts: "dangerously"`) + a 128 MiB segment cache + ExoPlayer buffers + the TV UI
   is a poor fit. Kotlin avoids the duplicated heap entirely.
6. **No localhost hop needed.** The Node design exists because EPlusTV feeds IPTV clients
   over HTTP. Inside an APK, M3U/XMLTV parsing and HLS playback are direct method calls;
   adding `127.0.0.1` HTTP would add a socket, a second JSON/serialisation boundary and a
   process to supervise for zero benefit.
7. **Security.** Option A would have to ship provider client secrets and device-identity
   generation in an APK — the exact practices the mission's Phase 3 forbids. Keystore-backed
   secrets and a native source model in option B are strictly safer.

## 4. Consequences (accepted)

- The 22 provider login flows are **not** available inside GameDay. Sources are
  user-authorised endpoints: M3U playlist, single HLS manifest, or an EPlusTV gateway on the
  LAN (which itself holds the provider sessions the user configured).
- GameDay has no server-side segment rewriting. It plays HLS directly; when a provider
  requires headers, the user supplies them per source and they are stored encrypted.
- No `sharp`-based thumbnails; guide artwork is used where a source provides `tvg-logo` /
  XMLTV `icon` URLs.
- Updates ship as signed APKs, never as downloaded JavaScript.

## 5. Why the provider layer was not ported (security/authorisation)

`services/nfl-handler.ts` and siblings embed OAuth client secrets, mint random device IDs
and spoof vendor user agents. Re-implementing those in a new client would be device identity
spoofing plus credential re-publication, and would break the mission's own restriction on
circumventing service protections. GameDay therefore integrates only through supported
mechanisms: the user's own authorised stream URLs / gateway.

## 6. What the Kotlin port implements

| EPlusTV concept | GameDay equivalent |
| --- | --- |
| `services/generate-m3u.ts` channel numbering/attributes | `epg/M3uParser.kt` + `Channel` model |
| `services/generate-xmltv.ts` XMLTV output | `epg/XmltvParser.kt` (SAX streaming reader) |
| `services/caching.ts` bounded in-memory cache | `GuidePruner` + `GuideCache` (atomic file, bounded size) |
| `services/playlist-handler.ts` HLS rewrite | not needed — ExoPlayer handles HLS, redirects, headers |
| `config/*.db` NeDB stores | `KeyValueStore` (SharedPreferences) + `SecretStore` (Keystore AES-GCM) + JSON guide cache |
| 4-hour `setInterval` refresh | inexact `AlarmManager` refresh + staleness check on start/resume |
| `/channels.m3u` + `/xmltv.xml` | source loader for EPlusTV gateway (`SourceKind.EPLUSTV`) |
| Node HTTP server | none (in-process repository) |

## 7. Future path if a specific provider is needed

Add one narrowly scoped, officially supported integration (documented API or the provider's
own Android SDK), authenticated as the user, with DRM left to the platform — not the whole
Node server. That keeps the APK small and the model honest.

## 8. Revisit criteria

Reconsider an embedded runtime only if (a) nodejs-mobile resumes armeabi-v7a support with a
current Node LTS, (b) a provider integration requires an official Node-only SDK, and (c)
measurements on a 1.5 GB device show acceptable cold start and heap. None is true today.
